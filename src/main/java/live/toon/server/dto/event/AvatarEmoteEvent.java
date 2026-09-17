package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

/** Broadcast to /topic/room/{id}/avatar-emote */
@Data
@Builder
public class AvatarEmoteEvent {
    private String userId;
    private String kind;
    private Integer value;
}
