package live.toon.server.dto;

import lombok.Data;

/** /app/chat payload */
@Data
public class ChatPayload {
    private String roomId;
    private String text;
}
