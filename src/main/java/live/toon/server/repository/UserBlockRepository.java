package live.toon.server.repository;

import live.toon.server.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    /** Directional — "did `blockerId` block `blockedId`". Used for room-entry: only the room owner's own block list bars someone from their house. */
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    Optional<UserBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** Either direction — used for private messages, where a block from either side severs contact. */
    @Query("""
        SELECT COUNT(b) > 0 FROM UserBlock b
        WHERE (b.blockerId = :a AND b.blockedId = :b) OR (b.blockerId = :b AND b.blockedId = :a)
        """)
    boolean existsBetweenEitherDirection(@Param("a") UUID a, @Param("b") UUID b);
}
