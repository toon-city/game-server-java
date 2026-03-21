package live.toon.server.controller;

import live.toon.server.dto.*;
import live.toon.server.dto.event.*;
import live.toon.server.model.UserPrincipal;
import live.toon.server.service.ChatService;
import live.toon.server.service.RoomStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomStompController {

    private final RoomStateService roomStateService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messaging;

    // ─── /app/join ────────────────────────────────────────────────────────────

    @MessageMapping("/join")
    @SendToUser("/queue/state")
    public RoomStateEvent join(@Payload JoinPayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        String roomId = payload.getRoomId();

        var roomOpt = roomStateService.join(
                roomId,
                sessionId(principal),
                user,
                payload.getAvatarOptions(),
                payload.getX(),
                payload.getY());

        if (roomOpt.isEmpty()) {
            messaging.convertAndSendToUser(
                    user.getUserId().toString(),
                    "/queue/error",
                    new ErrorEvent("NOT_FOUND", "Room not found: " + roomId));
            return null;
        }

        // Broadcast to room: user joined
        messaging.convertAndSend(
                roomTopic(roomId, "joined"),
                UserJoinedEvent.builder()
                        .userId(user.getUserId().toString())
                        .username(user.getUsername())
                        .avatarOptions(payload.getAvatarOptions())
                        .x(payload.getX())
                        .y(payload.getY())
                        .gender(user.getGender())
                        .rank(user.getRank())
                        .toonizLevel(user.getToonizLevel())
                        .build());

        return roomStateService.buildRoomState(roomOpt.get());
    }

    // ─── /app/leave ──────────────────────────────────────────────────────────

    @MessageMapping("/leave")
    public void leave(@Payload LeavePayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        roomStateService.leave(payload.getRoomId(), user.getUserId().toString());
        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "left"),
                UserLeftEvent.builder()
                        .userId(user.getUserId().toString())
                        .build());
    }

    // ─── /app/avatar/move ────────────────────────────────────────────────────

    @MessageMapping("/avatar/move")
    public void avatarMove(@Payload AvatarMovePayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        roomStateService.updatePosition(
                payload.getRoomId(),
                user.getUserId().toString(),
                payload.getX(), payload.getY(), payload.getDirection());

        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "avatar-move"),
                AvatarMoveEvent.builder()
                        .userId(user.getUserId().toString())
                        .x(payload.getX())
                        .y(payload.getY())
                        .direction(payload.getDirection())
                        .build());
    }

    // ─── /app/avatar/say ─────────────────────────────────────────────────────

    @MessageMapping("/avatar/say")
    public void avatarSay(@Payload AvatarSayPayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "avatar-say"),
                AvatarSayEvent.builder()
                        .userId(user.getUserId().toString())
                        .text(payload.getText())
                        .build());
    }

    // ─── /app/chat ───────────────────────────────────────────────────────────

    @MessageMapping("/chat")
    public void chat(@Payload ChatPayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        Long roomId = Long.parseLong(payload.getRoomId());
        var msg = chatService.save(roomId, user, payload.getText());

        // Rank comes from the in-memory ConnectedUser (loaded fresh from DB on join).
        int rank = roomStateService.getUser(payload.getRoomId(), user.getUserId().toString())
                .map(live.toon.server.model.ConnectedUser::getRank)
                .orElse(0);

        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "chat"),
                ChatEvent.builder()
                        .userId(user.getUserId().toString())
                        .username(user.getUsername())
                        .rank(rank)
                        .text(payload.getText())
                        .sentAt(msg.getSentAt() != null
                                ? msg.getSentAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                : OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .build());
    }

    // ─── /app/furniture/* ────────────────────────────────────────────────────

    @MessageMapping("/furniture/place")
    public void furniturePlace(@Payload FurniturePlacePayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        String instanceId = java.util.UUID.randomUUID().toString();
        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "furniture-place"),
                FurniturePlaceEvent.builder()
                        .instanceId(instanceId)
                        .baseId(payload.getBaseId())
                        .x(payload.getX())
                        .y(payload.getY())
                        .orientation(payload.getOrientation())
                        .placedByUserId(user.getUserId().toString())
                        .build());
    }

    @MessageMapping("/furniture/move")
    public void furnitureMove(@Payload FurnitureMovePayload payload, Principal principal) {
        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "furniture-move"),
                FurnitureMoveEvent.builder()
                        .instanceId(payload.getInstanceId())
                        .x(payload.getX())
                        .y(payload.getY())
                        .build());
    }

    @MessageMapping("/furniture/rotate")
    public void furnitureRotate(@Payload FurnitureRotatePayload payload, Principal principal) {
        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "furniture-rotate"),
                FurnitureRotateEvent.builder()
                        .instanceId(payload.getInstanceId())
                        .orientation(payload.getOrientation())
                        .build());
    }

    @MessageMapping("/furniture/remove")
    public void furnitureRemove(@Payload FurnitureRemovePayload payload, Principal principal) {
        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "furniture-remove"),
                FurnitureRemoveEvent.builder()
                        .instanceId(payload.getInstanceId())
                        .build());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UserPrincipal extractPrincipal(Principal principal) {
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) principal;
        return (UserPrincipal) auth.getPrincipal();
    }

    private String sessionId(Principal principal) {
        // Spring Security principal name is the session ID in STOMP context
        return principal.getName();
    }

    private static String roomTopic(String roomId, String event) {
        return "/topic/room/" + roomId + "/" + event;
    }

    // Simple error envelope
    record ErrorEvent(String code, String message) {}
}
