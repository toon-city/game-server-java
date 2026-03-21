package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

/** Broadcast to /topic/room/{id}/joined */
@Data
@Builder
public class UserJoinedEvent {
    private String userId;
    private String username;
    private Object avatarOptions;
    private double x;
    private double y;
    private String gender;
    private int rank;
    private int toonizLevel;
}
