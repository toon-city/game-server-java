package live.toon.server.dto;

import lombok.Data;

/** /app/join payload */
@Data
public class JoinPayload {
    private String roomId;
    /** Direction initiale de l'avatar (1-8). */
    private int direction;
    private double x;
    private double y;
}
