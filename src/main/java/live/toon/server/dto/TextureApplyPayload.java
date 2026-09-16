package live.toon.server.dto;

import lombok.Data;

@Data
public class TextureApplyPayload {
    private String roomId;
    /** Owned user_items row to apply — see FurniturePlacePayload's identical comment on why
     *  never a catalog item id. */
    private long userItemId;
    /** "WALL" or "FLOOR". */
    private String zoneType;
    /** Index into house_data's walls[]/floors[] array (matching zoneType). */
    private int zoneIndex;
}
