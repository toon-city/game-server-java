package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Read-only mapping of the shared {@code user_items} table (owned by game-api).
 */
@Entity
@Table(name = "user_items")
@Getter
@NoArgsConstructor
public class UserItem {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private boolean equipped;
}
