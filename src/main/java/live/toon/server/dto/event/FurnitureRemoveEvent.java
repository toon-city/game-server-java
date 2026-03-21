package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FurnitureRemoveEvent {
    private String instanceId;
}
