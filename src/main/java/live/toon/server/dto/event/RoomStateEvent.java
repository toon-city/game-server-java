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
    /** userId → { username, avatarOptions, x, y } */
    private List<UserSnapshot> users;

    @Data
    @Builder
    public static class UserSnapshot {
        private String userId;
        private String username;
        private Object avatarOptions;
        private double x;
        private double y;
        private int direction;
        private String gender;
        private int rank;
        private int toonizLevel;
    }
}
