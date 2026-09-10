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

    /** Possédé par cet utilisateur ET placé dans cette room précise — condition pour move/rotate/remove. */
    Optional<UserItem> findByIdAndUserIdAndPlacedInRoomId(Long id, UUID userId, Long placedInRoomId);

    /** Tout ce qui est actuellement placé dans une room, pour l'état envoyé au join. */
    List<UserItem> findByPlacedInRoomId(Long placedInRoomId);
}
