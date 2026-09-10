package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FurniturePlaceEvent {
    private String instanceId;
    private long baseId;
    private String spriteKey;
    private String spritePath;
    private String subType;
    private double x;
    private double y;
    private int orientation;
    private String placedByUserId;
}
