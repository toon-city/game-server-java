package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextureApplyEvent {
    private String instanceId;
    private long baseId;
    private String name;
    private String displayImage;
    private String spriteKey;
    private String spritePath;
    private String zoneType;
    private int zoneIndex;
    private String appliedByUserId;
}
