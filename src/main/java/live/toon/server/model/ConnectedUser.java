package live.toon.server.model;

import lombok.Builder;
import lombok.Data;

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
    /** Avatar options serialized as JSON string (forwarded as-is from client join payload). */
    private String avatarOptionsJson;
    /** MALE, FEMALE, NON_BINARY — may be null */
    private String gender;
    /** 0 = user, 1 = moderator, 2 = admin */
    private int rank;
    /** 0 = none, 1/2/3 */
    private int toonizLevel;
}
