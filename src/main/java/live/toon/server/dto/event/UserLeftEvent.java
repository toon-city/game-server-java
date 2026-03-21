package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

/** Broadcast to /topic/room/{id}/left */
@Data
@Builder
public class UserLeftEvent {
    private String userId;
}
