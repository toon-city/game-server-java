package live.toon.server.service;

import live.toon.server.dto.event.RoomStateEvent.FurnitureSnapshot;
import live.toon.server.entity.Item;
import live.toon.server.entity.Room;
import live.toon.server.entity.UserItem;
import live.toon.server.repository.RoomRepository;
import live.toon.server.repository.UserItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Furniture placement: pure DB CRUD on user_items (placedInRoomId/x/y/orientation),
 * no in-memory state to protect — unlike RoomStateService's ConnectedUser map,
 * furniture rows live only in Postgres, so there's nothing here to coordinate
 * with the per-room session lock. Kept as its own service rather than folded
 * into RoomStateService to keep that boundary clear (presence/session state
 * vs. plain persisted placement state).
 *
 * @Transactional on every method for the same reason RoomStateService.refreshClothing()
 * needs it: UserItem.item is a LAZY @ManyToOne, and every method here reads it
 * (subType/spriteKey/spritePath) — without an open Hibernate session that
 * throws LazyInitializationException the moment the proxy is touched.
 *
 * Permissions: a regular user may only place/move/rotate/remove furniture in a
 * room they own (rooms.owner_id), and only ever items from their own inventory.
 * Moderators get no special treatment here — only admins (rank >= ROLE_ADMIN)
 * bypass both checks entirely: they can place any unplaced item (owned by
 * anyone) into any room, and move/rotate/remove any piece already placed in
 * any room, regardless of who placed it.
 */
@Service
@RequiredArgsConstructor
public class FurnitureStateService {

    /** Only this ItemSubType can be placed in a room — floors/walls/wallpaper are room *shape*, not instances. */
    private static final String PLACEABLE_SUBTYPE = "PIECE";

    private final UserItemRepository userItemRepository;
    private final RoomRepository roomRepository;
    private final RoomAccessService roomAccessService;

    @Transactional
    public PlaceResult place(UUID userId, int rank, Long roomId, Long userItemId, double x, double y, int orientation) {
        assertCanManageRoom(userId, rank, roomId);

        // Admins can place furniture owned by anyone (moderation/decoration power);
        // a regular user (who only reaches this point as the room's own owner) may
        // only place items from their own inventory.
        UserItem ui = (rank >= RoomAccessService.ADMIN_RANK
                ? userItemRepository.findByIdAndPlacedInRoomIdIsNull(userItemId)
                : userItemRepository.findByIdAndUserIdAndPlacedInRoomIdIsNull(userItemId, userId))
                .orElseThrow(() -> new IllegalArgumentException("Objet introuvable ou déjà placé"));

        Item item = ui.getItem();
        if (!PLACEABLE_SUBTYPE.equals(item.getSubType())) {
            throw new IllegalArgumentException("Cet objet ne peut pas être placé dans une room");
        }

        ui.setPlacedInRoomId(roomId);
        ui.setX(x);
        ui.setY(y);
        ui.setOrientation(orientation);
        userItemRepository.save(ui);

        return toPlaceResult(ui);
    }

    @Transactional
    public void move(UUID userId, int rank, Long roomId, Long userItemId, double x, double y) {
        assertCanManageRoom(userId, rank, roomId);
        UserItem ui = placedHere(userId, rank, roomId, userItemId);
        ui.setX(x);
        ui.setY(y);
        userItemRepository.save(ui);
    }

    @Transactional
    public void rotate(UUID userId, int rank, Long roomId, Long userItemId, int orientation) {
        assertCanManageRoom(userId, rank, roomId);
        UserItem ui = placedHere(userId, rank, roomId, userItemId);
        ui.setOrientation(orientation);
        userItemRepository.save(ui);
    }

    @Transactional
    public void remove(UUID userId, int rank, Long roomId, Long userItemId) {
        assertCanManageRoom(userId, rank, roomId);
        UserItem ui = placedHere(userId, rank, roomId, userItemId);
        ui.setPlacedInRoomId(null);
        ui.setX(null);
        ui.setY(null);
        ui.setOrientation(null);
        userItemRepository.save(ui);
    }

    /** Everything currently placed in a room — used to build the join snapshot's furniture list. */
    @Transactional(readOnly = true)
    public List<FurnitureSnapshot> listPlaced(Long roomId) {
        return userItemRepository.findByPlacedInRoomId(roomId).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());
    }

    /**
     * Room-level gate: only the room's owner or an admin may touch furniture here at
     * all. Checked up front on every action (place included) so a non-owner can't
     * even probe whether a given userItemId is placed in someone else's room.
     */
    private void assertCanManageRoom(UUID userId, int rank, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room introuvable"));
        if (!roomAccessService.canManageRoom(room, userId, rank)) {
            throw new IllegalArgumentException("Vous ne pouvez gérer les meubles que dans vos propres rooms");
        }
    }

    /**
     * Move/rotate/remove target lookup. Room permission is already asserted by
     * assertCanManageRoom before this runs, so for an admin any piece placed in the
     * room qualifies (even one placed by another user); for a regular user (who can
     * only reach this point as the room's owner) we additionally require they own
     * the row, since under this permission model everything placed in their own
     * room was placed by them anyway — this is just defense-in-depth, not an extra
     * restriction in practice.
     */
    private UserItem placedHere(UUID userId, int rank, Long roomId, Long userItemId) {
        if (rank >= RoomAccessService.ADMIN_RANK) {
            return userItemRepository.findByIdAndPlacedInRoomId(userItemId, roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Objet introuvable dans cette room"));
        }
        return userItemRepository.findByIdAndUserIdAndPlacedInRoomId(userItemId, userId, roomId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Objet introuvable, pas à vous, ou pas placé dans cette room"));
    }

    private PlaceResult toPlaceResult(UserItem ui) {
        Item item = ui.getItem();
        return new PlaceResult(
                ui.getId().toString(),
                item.getId(),
                item.getName(),
                item.getDisplayImage(),
                item.getSpriteKey(),
                item.getSpritePath(),
                item.getSubType());
    }

    private FurnitureSnapshot toSnapshot(UserItem ui) {
        Item item = ui.getItem();
        return FurnitureSnapshot.builder()
                .instanceId(ui.getId().toString())
                .baseId(item.getId())
                .name(item.getName())
                .displayImage(item.getDisplayImage())
                .spriteKey(item.getSpriteKey())
                .spritePath(item.getSpritePath())
                .subType(item.getSubType())
                .x(ui.getX())
                .y(ui.getY())
                .orientation(ui.getOrientation())
                .placedByUserId(ui.getUserId().toString())
                .build();
    }

    /** Result of a successful place() — everything RoomStompController needs to broadcast.
     *  name/displayImage let the client show a preview (click outside edit mode) without a
     *  separate item lookup — see FurniturePreviewService/'furniture:click' in game-web. */
    public record PlaceResult(String instanceId, long baseId, String name, String displayImage,
                               String spriteKey, String spritePath, String subType) {}
}
