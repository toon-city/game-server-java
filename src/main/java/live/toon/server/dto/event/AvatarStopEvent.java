package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

/** Broadcast to /topic/room/{id}/avatar-stop */
@Data
@Builder
public class AvatarStopEvent {
    private String userId;
}
