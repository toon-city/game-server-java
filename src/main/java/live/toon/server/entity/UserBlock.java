package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Partial mapping of the shared `user_blocks` table (owned by game-api —
 * see live.toon.api.entity.UserBlock / V21__friends_and_blocks.sql). One
 * directional row: blockerId blocked blockedId. Read-only from here except
 * for the room-owner-bans-as-blacklist path in RoomModerationService — every
 * other write to this table goes through game-api's /api/friends/blocks.
 */
@Entity
@Table(name = "user_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
