package live.toon.server.dto;

import lombok.Data;

/** /app/avatar/move payload */
@Data
public class AvatarMovePayload {
    private String roomId;
    private double x;
    private double y;
    private int direction;
}
