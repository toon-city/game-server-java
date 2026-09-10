package live.toon.server.dto;

import lombok.Data;

@Data
public class FurniturePlacePayload {
    private String roomId;
    /**
     * Which owned user_items row to place — NOT the catalog item id. The
     * server derives baseId/spriteKey/spritePath from the owned row itself
     * (FurnitureStateService.place()); never trust a client-supplied baseId
     * for what gets placed, or a client could claim to place an item it
     * doesn't actually own.
     */
    private long userItemId;
    private double x;
    private double y;
    private int orientation;
}
