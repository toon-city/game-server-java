package live.toon.server.dto.event;

import lombok.Builder;
import lombok.Data;

/** Sent to /user/queue/private-message — both the sender and recipient receive this (echo = confirmation). */
@Data
@Builder
public class PrivateMessageEvent {
    private String fromUserId;
    private String fromUsername;
    private String toUserId;
    private String text;
    private String sentAt;
}
