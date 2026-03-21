package live.toon.server.dto;

import lombok.Data;

/** /app/avatar/say payload */
@Data
public class AvatarSayPayload {
    private String roomId;
    private String text;
}
