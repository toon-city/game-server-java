package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Sent to /user/queue/state upon successful join */
@Data
@Builder
public class RoomStateEvent {
    private String roomId;
    private String name;
    private String houseData;
    private List<UserSnapshot> users;
    private List<FurnitureSnapshot> furnitures;
    private List<TextureSnapshot> textures;

    /**
     * The joining user's own permission level in this room — never broadcast,
     * only ever sent to that user via /user/queue/state, so it's safe (and
     * necessary) for this to differ per viewer. Mirrors game-types'
     * RoomPermission ordinals: VIEW=0, EDIT=1, OWN=2. Only OWN is produced
     * today (room owner, or any admin — see RoomStateService.computePermission),
     * there's no intermediate co-editor tier yet.
     */
    private int yourPermission;

    @Data
    @Builder
    public static class UserSnapshot {
        private String userId;
        private String username;
        private int skinColor;
        private int hairColor;
        private Map<String, String> clothing;
        private double x;
        private double y;
        private int direction;
        private String gender;
        private int rank;
        private int toonizLevel;
    }

    /** One piece of furniture currently placed in the room — see FurnitureStateService.listPlaced(). */
    @Data
    @Builder
    public static class FurnitureSnapshot {
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

    /** One texture currently applied to a room zone (wall or floor) — see TextureStateService.listApplied(). */
    @Data
    @Builder
    public static class TextureSnapshot {
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
}
