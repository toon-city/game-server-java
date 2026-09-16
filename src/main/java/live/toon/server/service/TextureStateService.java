package live.toon.server.service;

import live.toon.server.dto.event.RoomStateEvent.TextureSnapshot;
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
 * Wallpaper/floor: same "possessable user_items row, placed in a room" model
 * as furniture (FurnitureStateService), but a texture targets one of the
 * room's own wall/floor zones (zoneType + zoneIndex — an index into
 * house_data's walls[]/floors[], see game-core's HouseParser and
 * FurnitureView's ground-anchor points for the client-side half of this) in
 * place of an x/y point. Kept as its own service rather than folded into
 * FurnitureStateService because the placement shape (and permission surface)
 * is different enough — no x/y/orientation, no move/rotate, "place" always
 * implies "replace whatever was already on that zone" (see apply()).
 *
 * Permissions mirror FurnitureStateService exactly: only the room's owner (or
 * any admin) may apply/remove textures, and a regular user may only apply
 * items from their own inventory.
 */
@Service
@RequiredArgsConstructor
public class TextureStateService {

    private static final List<String> PLACEABLE_SUBTYPES = List.of("WALLPAPER", "FLOOR");

    private final UserItemRepository userItemRepository;
    private final RoomRepository roomRepository;
    private final RoomAccessService roomAccessService;

    /**
     * Apply a texture item to a zone, replacing whatever was already there.
     * The previous occupant (if any) is sent back to its owner's inventory —
     * same "swap" semantics as InventoryService.equip() unequipping the old
     * item automatically, not a separate two-step call the client has to
     * orchestrate itself.
     */
    @Transactional
    public ApplyResult apply(UUID userId, int rank, Long roomId, Long userItemId, String zoneType, int zoneIndex) {
        assertCanManageRoom(userId, rank, roomId);
        assertValidZoneType(zoneType);

        UserItem ui = (rank >= RoomAccessService.ADMIN_RANK
                ? userItemRepository.findByIdAndPlacedInRoomIdIsNullAndEquippedFalse(userItemId)
                : userItemRepository.findByIdAndUserIdAndPlacedInRoomIdIsNullAndEquippedFalse(userItemId, userId))
                .orElseThrow(() -> new IllegalArgumentException("Objet introuvable ou déjà placé"));

        Item item = ui.getItem();
        if (!PLACEABLE_SUBTYPES.contains(item.getSubType())) {
            throw new IllegalArgumentException("Cet objet n'est pas un papier peint ou un revêtement de sol");
        }
        if (!item.getSubType().equals(zoneType.equals("WALL") ? "WALLPAPER" : "FLOOR")) {
            throw new IllegalArgumentException(
                    zoneType.equals("WALL") ? "Cet objet n'est pas un papier peint" : "Cet objet n'est pas un revêtement de sol");
        }

        // Whatever was already covering this zone goes back to its owner's
        // inventory — a zone can only ever show one texture at a time (also
        // DB-enforced, idx_user_items_room_zone), so silently orphaning the
        // old one instead would just leak it: not in anyone's inventory
        // anymore, not visible anywhere either.
        userItemRepository.findByPlacedInRoomIdAndZoneTypeAndZoneIndex(roomId, zoneType, zoneIndex)
                .ifPresent(previous -> {
                    previous.setPlacedInRoomId(null);
                    previous.setZoneType(null);
                    previous.setZoneIndex(null);
                    userItemRepository.save(previous);
                });

        ui.setPlacedInRoomId(roomId);
        ui.setZoneType(zoneType);
        ui.setZoneIndex(zoneIndex);
        userItemRepository.save(ui);

        return toApplyResult(ui);
    }

    /** Removes whatever texture currently covers a zone, if any — sends it back to its owner's inventory. */
    @Transactional
    public void remove(UUID userId, int rank, Long roomId, String zoneType, int zoneIndex) {
        assertCanManageRoom(userId, rank, roomId);
        assertValidZoneType(zoneType);

        UserItem ui = userItemRepository.findByPlacedInRoomIdAndZoneTypeAndZoneIndex(roomId, zoneType, zoneIndex)
                .orElseThrow(() -> new IllegalArgumentException("Aucune texture appliquée sur cette zone"));

        ui.setPlacedInRoomId(null);
        ui.setZoneType(null);
        ui.setZoneIndex(null);
        userItemRepository.save(ui);
    }

    /** Everything currently applied in a room — used to build the join snapshot's texture list. */
    @Transactional(readOnly = true)
    public List<TextureSnapshot> listApplied(Long roomId) {
        return userItemRepository.findByPlacedInRoomIdAndZoneTypeIsNotNull(roomId).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());
    }

    private void assertValidZoneType(String zoneType) {
        if (!"WALL".equals(zoneType) && !"FLOOR".equals(zoneType)) {
            throw new IllegalArgumentException("zoneType invalide : " + zoneType);
        }
    }

    /** Same room-level gate as FurnitureStateService — see its own comment. */
    private void assertCanManageRoom(UUID userId, int rank, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room introuvable"));
        if (!roomAccessService.canManageRoom(room, userId, rank)) {
            throw new IllegalArgumentException("Vous ne pouvez gérer les textures que dans vos propres rooms");
        }
    }

    private ApplyResult toApplyResult(UserItem ui) {
        Item item = ui.getItem();
        return new ApplyResult(
                ui.getId().toString(),
                item.getId(),
                item.getName(),
                item.getDisplayImage(),
                item.getSpriteKey(),
                item.getSpritePath(),
                ui.getZoneType(),
                ui.getZoneIndex());
    }

    private TextureSnapshot toSnapshot(UserItem ui) {
        Item item = ui.getItem();
        return TextureSnapshot.builder()
                .instanceId(ui.getId().toString())
                .baseId(item.getId())
                .name(item.getName())
                .displayImage(item.getDisplayImage())
                .spriteKey(item.getSpriteKey())
                .spritePath(item.getSpritePath())
                .zoneType(ui.getZoneType())
                .zoneIndex(ui.getZoneIndex())
                .appliedByUserId(ui.getUserId().toString())
                .build();
    }

    /** Result of a successful apply() — everything RoomStompController needs to broadcast. */
    public record ApplyResult(String instanceId, long baseId, String name, String displayImage,
                               String spriteKey, String spritePath, String zoneType, int zoneIndex) {}
}
