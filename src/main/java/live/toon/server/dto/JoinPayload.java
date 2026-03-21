package live.toon.server.dto;

import lombok.Data;

/** /app/join payload */
@Data
public class JoinPayload {
    private String roomId;
    /** Avatar options as a JSON object (forwarded as-is). */
    private Object avatarOptions;
    private double x;
    private double y;
}
