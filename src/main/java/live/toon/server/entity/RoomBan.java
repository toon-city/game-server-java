package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Blocks one user from re-joining one specific room. Permanent — no expiry, no unban UI yet. */
@Entity
@Table(name = "room_bans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "banned_by_id")
    private UUID bannedById;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "banned_at", updatable = false)
    private OffsetDateTime bannedAt;
}
