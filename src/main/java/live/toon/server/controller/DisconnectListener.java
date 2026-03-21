package live.toon.server.controller;

import live.toon.server.dto.event.UserLeftEvent;
import live.toon.server.service.RoomStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisconnectListener {

    private final RoomStateService roomStateService;
    private final SimpMessagingTemplate messaging;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        roomStateService.onDisconnect(sessionId).ifPresent(membership -> {
            log.info("Session {} disconnected from room {}", sessionId, membership.roomId());
            messaging.convertAndSend(
                    "/topic/room/" + membership.roomId() + "/left",
                    UserLeftEvent.builder()
                            .userId(membership.userId())
                            .build());
        });
    }
}
