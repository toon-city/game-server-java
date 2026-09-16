package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Partial mapping of the shared {@code users} table (owned by game-api).
 * The game-server reads profile data and writes presence fields only
 * (online, current_room_id).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
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

    /** Whether the user is currently connected. */
    @Column(nullable = false)
    private boolean online = false;

    /** ID of the room the user is currently in, or null. */
    @Column(name = "current_room_id")
    private Long currentRoomId;

    /** Last login timestamp — updated on every WebSocket connection. */
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /** Couleur de peau de l'avatar (valeur hexadécimale, ex: 0xf7ceaf). */
    @Column(name = "skin_color")
    private Integer skinColor;

    /** Mirrors game-api's ban flag — read-only here, used to reject STOMP CONNECT. */
    @Column(nullable = false)
    private boolean banned = false;

    @Column(name = "banned_until")
    private OffsetDateTime bannedUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metier_id")
    private Metier metier;

    /** Overlay flag — see RoomStateService.buildClothingMap(). */
    @Column(name = "work_outfit_active", nullable = false)
    private boolean workOutfitActive = false;
}
