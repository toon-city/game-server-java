package live.toon.server.model;

import lombok.Data;

import java.security.Principal;
import java.util.UUID;

@Data
public class UserPrincipal implements Principal {
    private final UUID userId;
    private final String username;
    /** MALE, FEMALE, NON_BINARY — may be null */
    private final String gender;
    /** 0 = user, 1 = moderator, 2 = admin */
    private final int rank;
    /** 0 = none, 1/2/3 */
    private final int toonizLevel;

    /**
     * Returns the userId as string.
     * Spring STOMP uses this to route /user/queue/* messages to the right session.
     */
    @Override
    public String getName() {
        return userId.toString();
    }
}
