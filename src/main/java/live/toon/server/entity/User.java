package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Read-only mapping of the shared {@code users} table (owned by game-api).
 * The game-server never writes to this table — it only reads profile data
 * (gender, rank, toonizLevel) so that room events always carry fresh values.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    /** MALE, FEMALE, NON_BINARY — may be null */
    @Column(length = 20)
    private String gender;

    /** 0 = user, 1 = moderator, 2 = admin */
    @Column(nullable = false)
    private int rank;

    /** 0 = none, 1/2/3 */
    @Column(name = "tooniz_level", nullable = false)
    private int toonizLevel;
}
