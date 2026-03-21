package live.toon.server.dto;

import lombok.Data;

@Data
public class FurniturePlacePayload {
    private String roomId;
    private long baseId;
    private double x;
    private double y;
    private int orientation;
}
