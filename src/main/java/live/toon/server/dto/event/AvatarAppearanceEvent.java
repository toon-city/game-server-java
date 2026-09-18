package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Broadcast to /topic/room/{id}/avatar-appearance when a user changes their outfit. */
@Data
@Builder
public class AvatarAppearanceEvent {
    private String userId;
    private int skinColor;
    private int hairColor;
    private Map<String, String> clothing;
}
