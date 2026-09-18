package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Broadcast to /topic/room/{id}/joined */
@Data
@Builder
public class UserJoinedEvent {
    private String userId;
    private String username;
    private int skinColor;
    private int hairColor;
    private Map<String, String> clothing;
    private double x;
    private double y;
    private int direction;
    private String gender;
    private int rank;
    private int toonizLevel;
}
