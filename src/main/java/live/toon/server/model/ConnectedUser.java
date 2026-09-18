package live.toon.server.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/** Snapshot of a connected user inside a room (in-memory only). */
@Data
@Builder
public class ConnectedUser {
    private UUID userId;
    private String username;
    private String sessionId;
    private double x;
    private double y;
    private int direction;
    /** Couleur de peau (ex: 0xf7ceaf). */
    private int skinColor;
    /** Couleur de cheveux (0xffffff = pas de teinte, garde la couleur dessinée). */
    private int hairColor;
    /** Vêtements équipés : spriteKey → spritePath (ex: "hair" → "hair7"). */
    private Map<String, String> clothing;
    /** MALE, FEMALE, NON_BINARY — may be null */
    private String gender;
    /** 0 = user, 1 = moderator, 2 = admin */
    private int rank;
    /** 0 = none, 1/2/3 */
    private int toonizLevel;
}
