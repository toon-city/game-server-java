package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

/** Broadcast to /topic/room/{id}/avatar-say */
@Data
@Builder
public class AvatarSayEvent {
    private String userId;
    private String text;
}
