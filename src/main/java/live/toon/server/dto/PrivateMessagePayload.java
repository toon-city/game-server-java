package live.toon.server.dto;

import lombok.Data;

@Data
public class PrivateMessagePayload {
    private String roomId;
    private String toUserId;
    private String text;
}
