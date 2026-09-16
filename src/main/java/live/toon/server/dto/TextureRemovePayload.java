package live.toon.server.dto;

import lombok.Data;

@Data
public class TextureRemovePayload {
    private String roomId;
    /** "WALL" or "FLOOR". */
    private String zoneType;
    /** Index into house_data's walls[]/floors[] array (matching zoneType). */
    private int zoneIndex;
}
