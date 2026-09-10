package live.toon.server.repository;

import live.toon.server.entity.RoomBan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomBanRepository extends JpaRepository<RoomBan, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, UUID userId);

    /** For upserting the reason/timestamp when the same user gets room-banned again. */
    Optional<RoomBan> findByRoomIdAndUserId(Long roomId, UUID userId);
}
