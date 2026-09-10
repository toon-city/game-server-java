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

    @Data
    @Builder
    public static class UserSnapshot {
        private String userId;
        private String username;
        private int skinColor;
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
        private String spriteKey;
        private String spritePath;
        private String subType;
        private double x;
        private double y;
        private int orientation;
        private String placedByUserId;
    }
}
