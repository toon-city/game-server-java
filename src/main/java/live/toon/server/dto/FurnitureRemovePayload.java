package live.toon.server.dto;

import lombok.Data;

@Data
public class FurnitureRemovePayload {
    private String roomId;
    private String instanceId;
}
