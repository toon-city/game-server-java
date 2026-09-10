package live.toon.server.dto;

import lombok.Data;

@Data
public class RoomKickPayload {
    private String roomId;
    private String targetUserId;
}
