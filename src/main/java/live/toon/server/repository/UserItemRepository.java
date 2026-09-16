package live.toon.server.repository;

import live.toon.server.entity.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    /** Retourne tous les vêtements actuellement équipés par un utilisateur. */
    List<UserItem> findByUserIdAndEquippedTrueAndItemItemType(UUID userId, String itemType);

    /** Possédé par cet utilisateur et pas déjà placé quelque part — condition pour un placement. */
    Optional<UserItem> findByIdAndUserIdAndPlacedInRoomIdIsNull(Long id, UUID userId);

    /** Admin-only lookup: no owner check, used to place furniture owned by anyone. */
    Optional<UserItem> findByIdAndPlacedInRoomIdIsNull(Long id);

    /** Possédé par cet utilisateur ET placé dans cette room précise — condition pour move/rotate/remove. */
    Optional<UserItem> findByIdAndUserIdAndPlacedInRoomId(Long id, UUID userId, Long placedInRoomId);

    /** Tout ce qui est actuellement placé dans une room, pour l'état envoyé au join. */
    List<UserItem> findByPlacedInRoomId(Long placedInRoomId);

    /** Admin-only lookup: no owner check, used to manage furniture placed by anyone. */
    Optional<UserItem> findByIdAndPlacedInRoomId(Long id, Long placedInRoomId);

    /** Meubles (x/y) placés dans une room — exclut les textures de zone (murs/sols),
     *  qui n'ont pas de x/y et casseraient FurnitureStateService.toSnapshot. */
    List<UserItem> findByPlacedInRoomIdAndZoneTypeIsNull(Long placedInRoomId);

    /** Textures de zone (murs/sols) actuellement appliquées dans une room. */
    List<UserItem> findByPlacedInRoomIdAndZoneTypeIsNotNull(Long placedInRoomId);

    /** Possédé par cet utilisateur, pas placé, et pas équipé — condition pour appliquer une texture. */
    Optional<UserItem> findByIdAndUserIdAndPlacedInRoomIdIsNullAndEquippedFalse(Long id, UUID userId);

    /** Admin-only : idem sans le contrôle de propriétaire. */
    Optional<UserItem> findByIdAndPlacedInRoomIdIsNullAndEquippedFalse(Long id);

    /** Texture actuellement appliquée à cette zone précise, si elle existe (pour le "swap"
     *  atomique appliquer-par-dessus-l'existant, et pour retirer via zone plutôt que par id). */
    Optional<UserItem> findByPlacedInRoomIdAndZoneTypeAndZoneIndex(Long placedInRoomId, String zoneType, Integer zoneIndex);
}
