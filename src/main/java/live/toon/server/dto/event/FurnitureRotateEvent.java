package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FurnitureRotateEvent {
    private String instanceId;
    private int orientation;
}
