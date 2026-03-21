package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

/** Broadcast to /topic/room/{id}/avatar-move */
@Data
@Builder
public class AvatarMoveEvent {
    private String userId;
    private double x;
    private double y;
    private int direction;
}
