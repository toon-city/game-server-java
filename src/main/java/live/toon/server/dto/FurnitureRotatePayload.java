package live.toon.server.dto;

import lombok.Data;

@Data
public class FurnitureRotatePayload {
    private String roomId;
    private String instanceId;
    private int orientation;
}
