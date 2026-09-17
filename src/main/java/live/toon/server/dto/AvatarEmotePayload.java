package live.toon.server.dto;

import lombok.Data;

/** /app/avatar/emote payload */
@Data
public class AvatarEmotePayload {
    private String roomId;
    private String kind;
    private Integer value;
}
