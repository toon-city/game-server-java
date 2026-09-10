package live.toon.server.dto;

import lombok.Data;

@Data
public class RoomBanPayload {
    private String roomId;
    private String targetUserId;
    private String reason;
}
