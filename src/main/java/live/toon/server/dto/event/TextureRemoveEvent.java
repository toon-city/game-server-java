package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextureRemoveEvent {
    private String zoneType;
    private int zoneIndex;
}
