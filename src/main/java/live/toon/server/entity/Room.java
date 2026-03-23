package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

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
