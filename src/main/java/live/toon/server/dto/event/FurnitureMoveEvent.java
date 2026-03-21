package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FurnitureMoveEvent {
    private String instanceId;
    private double x;
    private double y;
}
