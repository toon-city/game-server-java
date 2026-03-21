package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

/** Broadcast to /topic/room/{id}/chat */
@Data
@Builder
public class ChatEvent {
    private String userId;
    private String username;
    /** 0 = user, 1 = moderator, 2 = admin */
    private int rank;
    private String text;
    private String sentAt;
}
