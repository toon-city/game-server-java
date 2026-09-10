package live.toon.server.service;

import live.toon.server.dto.event.RoomStateEvent.FurnitureSnapshot;
import live.toon.server.entity.Item;
import live.toon.server.entity.UserItem;
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
 */
@Service
@RequiredArgsConstructor
public class FurnitureStateService {

    /** Only this ItemSubType can be placed in a room — floors/walls/wallpaper are room *shape*, not instances. */
    private static final String PLACEABLE_SUBTYPE = "PIECE";

    private final UserItemRepository userItemRepository;

    @Transactional
    public PlaceResult place(UUID userId, Long roomId, Long userItemId, double x, double y, int orientation) {
        UserItem ui = userItemRepository.findByIdAndUserIdAndPlacedInRoomIdIsNull(userItemId, userId)
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
    public void move(UUID userId, Long roomId, Long userItemId, double x, double y) {
        UserItem ui = ownedAndPlacedHere(userId, roomId, userItemId);
        ui.setX(x);
        ui.setY(y);
        userItemRepository.save(ui);
    }

    @Transactional
    public void rotate(UUID userId, Long roomId, Long userItemId, int orientation) {
        UserItem ui = ownedAndPlacedHere(userId, roomId, userItemId);
        ui.setOrientation(orientation);
        userItemRepository.save(ui);
    }

    @Transactional
    public void remove(UUID userId, Long roomId, Long userItemId) {
        UserItem ui = ownedAndPlacedHere(userId, roomId, userItemId);
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

    /** Owner-only: move/rotate/remove all require the caller to both own the row AND have it placed in THIS room. */
    private UserItem ownedAndPlacedHere(UUID userId, Long roomId, Long userItemId) {
        return userItemRepository.findByIdAndUserIdAndPlacedInRoomId(userItemId, userId, roomId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Objet introuvable, pas à vous, ou pas placé dans cette room"));
    }

    private PlaceResult toPlaceResult(UserItem ui) {
        Item item = ui.getItem();
        return new PlaceResult(
                ui.getId().toString(),
                item.getId(),
                item.getSpriteKey(),
                item.getSpritePath(),
                item.getSubType());
    }

    private FurnitureSnapshot toSnapshot(UserItem ui) {
        Item item = ui.getItem();
        return FurnitureSnapshot.builder()
                .instanceId(ui.getId().toString())
                .baseId(item.getId())
                .spriteKey(item.getSpriteKey())
                .spritePath(item.getSpritePath())
                .subType(item.getSubType())
                .x(ui.getX())
                .y(ui.getY())
                .orientation(ui.getOrientation())
                .placedByUserId(ui.getUserId().toString())
                .build();
    }

    /** Result of a successful place() — everything RoomStompController needs to broadcast. */
    public record PlaceResult(String instanceId, long baseId, String spriteKey, String spritePath, String subType) {}
}
