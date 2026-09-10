package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "house_data", columnDefinition = "TEXT")
    private String houseData;

    /** Raw FK, not a @ManyToOne — same pattern as UserItem.userId, we only ever need the id for comparison. */
    @Column(name = "owner_id")
    private UUID ownerId;

    @Builder.Default
    @Column(name = "max_users", nullable = false)
    private int maxUsers = 50;

    /** Number of users currently present in this room. Persisted for fast lobby queries. */
    @Builder.Default
    @Column(name = "user_count", nullable = false)
    private int userCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
