package live.toon.server.model;

import lombok.Data;

import java.util.UUID;

@Data
public class UserPrincipal {
    private final UUID userId;
    private final String username;
    /** MALE, FEMALE, NON_BINARY — may be null */
    private final String gender;
    /** 0 = user, 1 = moderator, 2 = admin */
    private final int rank;
    /** 0 = none, 1/2/3 */
    private final int toonizLevel;
}
