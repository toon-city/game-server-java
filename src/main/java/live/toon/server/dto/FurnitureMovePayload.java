package live.toon.server.dto;

import lombok.Data;

@Data
public class FurnitureMovePayload {
    private String roomId;
    private String instanceId;
    private double x;
    private double y;
}
