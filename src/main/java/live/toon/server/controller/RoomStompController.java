package live.toon.server.controller;

import live.toon.server.dto.*;
import live.toon.server.dto.event.*;
import live.toon.server.model.UserPrincipal;
import live.toon.server.service.ChatService;
import live.toon.server.service.FurnitureStateService;
import live.toon.server.service.RoomModerationService;
import live.toon.server.service.RoomStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomStompController {

    private final RoomStateService roomStateService;
    private final ChatService chatService;
    private final FurnitureStateService furnitureStateService;
    private final RoomModerationService roomModerationService;
    private final SimpMessagingTemplate messaging;

    // ─── /app/join ────────────────────────────────────────────────────────────

    @MessageMapping("/join")
    @SendToUser("/queue/state")
    public RoomStateEvent join(@Payload JoinPayload payload, Principal principal,
                               SimpMessageHeaderAccessor headerAccessor) {
        UserPrincipal user = extractPrincipal(principal);
        String roomId = payload.getRoomId();
        // Use the real STOMP session ID (not principal.getName())
        String sessionId = headerAccessor.getSessionId();

        if (roomModerationService.isRoomBanned(Long.parseLong(roomId), user.getUserId())) {
            messaging.convertAndSendToUser(
                    user.getUserId().toString(),
                    "/queue/error",
                    new ErrorEvent("ROOM_BANNED", "Vous êtes banni de cette room."));
            return null;
        }

        var resultOpt = roomStateService.join(
                roomId, sessionId, user,
                payload.getDirection(), payload.getX(), payload.getY());

        if (resultOpt.isEmpty()) {
            messaging.convertAndSendToUser(
                    user.getUserId().toString(),
                    "/queue/error",
                    new ErrorEvent("NOT_FOUND", "Room not found: " + roomId));
            return null;
        }

        RoomStateService.JoinResult result = resultOpt.get();

        // If an old session was evicted, notify it so the client can disconnect gracefully.
        // We send directly to the session-specific internal destination (/queue/kicked-user{sessionId})
        // to avoid broadcasting to the NEW session which has the same userId.
        if (result.hasEviction()) {
            messaging.convertAndSend(
                    "/queue/kicked-user" + result.evictedSessionId(),
                    new KickedEvent("DUPLICATE_SESSION",
                            "Vous avez été déconnecté car vous vous êtes connecté ailleurs."));
            log.info("Sent kick to session {} (duplicate session)", result.evictedSessionId());

            // Tell the old room the evicted user is gone, otherwise their avatar stays
            // frozen on-screen for everyone still in that room.
            if (result.evictedRoomId() != null) {
                messaging.convertAndSend(
                        roomTopic(result.evictedRoomId(), "left"),
                        UserLeftEvent.builder()
                                .userId(user.getUserId().toString())
                                .build());
            }
        }

        // Broadcast to room: user joined
        messaging.convertAndSend(
                roomTopic(roomId, "joined"),
                UserJoinedEvent.builder()
                        .userId(user.getUserId().toString())
                        .username(user.getUsername())
                        .skinColor(roomStateService.getUser(roomId, user.getUserId().toString())
                                .map(live.toon.server.model.ConnectedUser::getSkinColor).orElse(0xf7ceaf))
                        .clothing(roomStateService.getUser(roomId, user.getUserId().toString())
                                .map(live.toon.server.model.ConnectedUser::getClothing).orElse(java.util.Map.of()))
                        .x(payload.getX())
                        .y(payload.getY())
                        .direction(payload.getDirection())
                        .gender(user.getGender())
                        .rank(user.getRank())
                        .toonizLevel(user.getToonizLevel())
                        .build());

        // Snapshot taken atomically with the join inside the service — not rebuilt
        // here, where a concurrent joiner could slip in or out of it.
        return result.roomState();
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

    // ─── /app/avatar/stop ────────────────────────────────────────────────────

    @MessageMapping("/avatar/stop")
    public void avatarStop(@Payload AvatarStopPayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        messaging.convertAndSend(
                roomTopic(payload.getRoomId(), "avatar-stop"),
                AvatarStopEvent.builder()
                        .userId(user.getUserId().toString())
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
    // All four persist via FurnitureStateService before broadcasting — placing/
    // moving/rotating/removing used to be pure broadcasts with no persistence
    // at all (a random UUID minted fresh per place() call, never stored), so
    // furniture placed by one player was invisible to anyone who joined the
    // room afterward, and vanished the moment the room emptied out. Broadcast
    // now only happens on success; a rejected action (not owned, already
    // placed elsewhere, wrong subtype, not placed here) gets a /queue/error
    // reply instead, same pattern as /app/join's NOT_FOUND case below.

    @MessageMapping("/furniture/place")
    public void furniturePlace(@Payload FurniturePlacePayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        try {
            var result = furnitureStateService.place(
                    user.getUserId(), user.getRank(), Long.parseLong(payload.getRoomId()),
                    payload.getUserItemId(), payload.getX(), payload.getY(), payload.getOrientation());
            messaging.convertAndSend(
                    roomTopic(payload.getRoomId(), "furniture-place"),
                    FurniturePlaceEvent.builder()
                            .instanceId(result.instanceId())
                            .baseId(result.baseId())
                            .spriteKey(result.spriteKey())
                            .spritePath(result.spritePath())
                            .subType(result.subType())
                            .x(payload.getX())
                            .y(payload.getY())
                            .orientation(payload.getOrientation())
                            .placedByUserId(user.getUserId().toString())
                            .build());
        } catch (IllegalArgumentException e) {
            sendFurnitureError(user, e);
        }
    }

    @MessageMapping("/furniture/move")
    public void furnitureMove(@Payload FurnitureMovePayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        try {
            furnitureStateService.move(
                    user.getUserId(), user.getRank(), Long.parseLong(payload.getRoomId()),
                    Long.parseLong(payload.getInstanceId()), payload.getX(), payload.getY());
            messaging.convertAndSend(
                    roomTopic(payload.getRoomId(), "furniture-move"),
                    FurnitureMoveEvent.builder()
                            .instanceId(payload.getInstanceId())
                            .x(payload.getX())
                            .y(payload.getY())
                            .build());
        } catch (IllegalArgumentException e) {
            sendFurnitureError(user, e);
        }
    }

    @MessageMapping("/furniture/rotate")
    public void furnitureRotate(@Payload FurnitureRotatePayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        try {
            furnitureStateService.rotate(
                    user.getUserId(), user.getRank(), Long.parseLong(payload.getRoomId()),
                    Long.parseLong(payload.getInstanceId()), payload.getOrientation());
            messaging.convertAndSend(
                    roomTopic(payload.getRoomId(), "furniture-rotate"),
                    FurnitureRotateEvent.builder()
                            .instanceId(payload.getInstanceId())
                            .orientation(payload.getOrientation())
                            .build());
        } catch (IllegalArgumentException e) {
            sendFurnitureError(user, e);
        }
    }

    @MessageMapping("/furniture/remove")
    public void furnitureRemove(@Payload FurnitureRemovePayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        try {
            furnitureStateService.remove(
                    user.getUserId(), user.getRank(), Long.parseLong(payload.getRoomId()),
                    Long.parseLong(payload.getInstanceId()));
            messaging.convertAndSend(
                    roomTopic(payload.getRoomId(), "furniture-remove"),
                    FurnitureRemoveEvent.builder()
                            .instanceId(payload.getInstanceId())
                            .build());
        } catch (IllegalArgumentException e) {
            sendFurnitureError(user, e);
        }
    }

    private void sendFurnitureError(UserPrincipal user, IllegalArgumentException e) {
        messaging.convertAndSendToUser(
                user.getUserId().toString(),
                "/queue/error",
                new ErrorEvent("FURNITURE_ACTION_FAILED", e.getMessage()));
    }

    // ─── /app/room/kick, /app/room/ban, /app/chat/private ──────────────────────
    // Permission (room owner or admin) is re-checked server-side on every call
    // by RoomModerationService — the client's yourPermission-gated UI is a
    // convenience, never the actual boundary.

    @MessageMapping("/room/kick")
    public void roomKick(@Payload RoomKickPayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        try {
            roomModerationService.kick(
                    user.getUserId(), user.getRank(), Long.parseLong(payload.getRoomId()),
                    UUID.fromString(payload.getTargetUserId()), null);
        } catch (IllegalArgumentException e) {
            sendModerationError(user, e);
        }
    }

    @MessageMapping("/room/ban")
    public void roomBan(@Payload RoomBanPayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        try {
            roomModerationService.banFromRoom(
                    user.getUserId(), user.getRank(), Long.parseLong(payload.getRoomId()),
                    UUID.fromString(payload.getTargetUserId()), payload.getReason());
        } catch (IllegalArgumentException e) {
            sendModerationError(user, e);
        }
    }

    @MessageMapping("/chat/private")
    public void privateMessage(@Payload PrivateMessagePayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        try {
            roomModerationService.sendPrivateMessage(
                    user.getUserId(), user.getUsername(), Long.parseLong(payload.getRoomId()),
                    UUID.fromString(payload.getToUserId()), payload.getText());
        } catch (IllegalArgumentException e) {
            sendModerationError(user, e);
        }
    }

    private void sendModerationError(UserPrincipal user, IllegalArgumentException e) {
        messaging.convertAndSendToUser(
                user.getUserId().toString(),
                "/queue/error",
                new ErrorEvent("MODERATION_ACTION_FAILED", e.getMessage()));
    }

    // ─── /app/avatar/clothing/refresh ────────────────────────────────────────

    @MessageMapping("/avatar/clothing/refresh")
    public void clothingRefresh(@Payload ClothingRefreshPayload payload, Principal principal) {
        UserPrincipal user = extractPrincipal(principal);
        String roomId = payload.roomId();
        String userId = user.getUserId().toString();

        roomStateService.refreshClothing(roomId, userId).ifPresent(cu ->
            messaging.convertAndSend(
                    roomTopic(roomId, "avatar-appearance"),
                    AvatarAppearanceEvent.builder()
                            .userId(userId)
                            .skinColor(cu.getSkinColor())
                            .clothing(cu.getClothing())
                            .build()));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UserPrincipal extractPrincipal(Principal principal) {
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) principal;
        return (UserPrincipal) auth.getPrincipal();
    }

    private static String roomTopic(String roomId, String event) {
        return "/topic/room/" + roomId + "/" + event;
    }

    record ClothingRefreshPayload(String roomId) {}
}
