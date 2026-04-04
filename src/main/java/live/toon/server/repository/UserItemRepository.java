package live.toon.server.repository;

import live.toon.server.entity.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    /** Retourne tous les vêtements actuellement équipés par un utilisateur. */
    List<UserItem> findByUserIdAndEquippedTrueAndItemItemType(UUID userId, String itemType);
}
