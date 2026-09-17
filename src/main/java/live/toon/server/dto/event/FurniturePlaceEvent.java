package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FurniturePlaceEvent {
    private String instanceId;
    private long baseId;
    private String name;
    private String displayImage;
    private String spriteKey;
    private String spritePath;
    private String subType;
    /** Old game's STYPE (18/19/20) — see game-api's Item.renderType. */
    private int type;
    private double x;
    private double y;
    private int orientation;
    private String placedByUserId;
}
