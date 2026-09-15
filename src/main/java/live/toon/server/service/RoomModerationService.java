package live.toon.server.service;

import live.toon.server.dto.event.KickedEvent;
import live.toon.server.dto.event.PrivateMessageEvent;
import live.toon.server.dto.event.UserLeftEvent;
import live.toon.server.entity.PrivateMessage;
import live.toon.server.entity.Room;
import live.toon.server.entity.RoomBan;
import live.toon.server.entity.UserBlock;
import live.toon.server.model.ConnectedUser;
import live.toon.server.repository.PrivateMessageRepository;
import live.toon.server.repository.RoomBanRepository;
import live.toon.server.repository.RoomRepository;
import live.toon.server.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Room-scoped moderation (kick/ban) plus the site-ban's immediate-kick path.
 * Unlike FurnitureStateService, this owns SimpMessagingTemplate directly:
 * both RoomStompController (room kick/ban, private message) and
 * InternalController (site-ban kick, triggered by game-api, no room
 * context at all) need to reach a session that isn't necessarily the
 * caller's own, so the messaging calls belong here rather than being
 * split across two different controllers.
 */
@Service
@RequiredArgsConstructor
public class RoomModerationService {

    private final RoomRepository roomRepository;
    private final RoomBanRepository roomBanRepository;
    private final UserBlockRepository userBlockRepository;
    private final PrivateMessageRepository privateMessageRepository;
    private final RoomAccessService roomAccessService;
    private final RoomStateService roomStateService;
    private final SimpMessagingTemplate messaging;

    @Transactional
    public void kick(UUID actorId, int actorRank, Long roomId, UUID targetUserId, String reason) {
        assertCanManageRoom(actorId, actorRank, roomId);
        doKick(roomId, targetUserId, "ROOM_KICKED",
                reason != null ? reason : "Vous avez été expulsé de cette room.");
    }

    @Transactional
    public void banFromRoom(UUID actorId, int actorRank, Long roomId, UUID targetUserId, String reason) {
        Room room = assertCanManageRoom(actorId, actorRank, roomId);

        RoomBan ban = roomBanRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseGet(RoomBan::new);
        ban.setRoomId(roomId);
        ban.setUserId(targetUserId);
        ban.setBannedById(actorId);
        ban.setReason(reason);
        roomBanRepository.save(ban);

        // Banning as the room's OWNER also blacklists the target (same table
        // game-api's /api/friends/blocks writes to) — bars them from every
        // house this owner has (see RoomStompController.join's block check),
        // blocks their private messages (sendPrivateMessage below), and hides
        // their public chat from the owner in any room (game-web's
        // ChatComponent filters client-side). An admin moderating a room they
        // don't own doesn't trigger this — that's not a personal block.
        if (actorId.equals(room.getOwnerId())
                && !userBlockRepository.existsByBlockerIdAndBlockedId(actorId, targetUserId)) {
            userBlockRepository.save(UserBlock.builder()
                    .blockerId(actorId)
                    .blockedId(targetUserId)
                    .build());
        }

        doKick(roomId, targetUserId, "ROOM_BANNED",
                reason != null ? "Vous avez été banni de cette room : " + reason : "Vous avez été banni de cette room.");
    }

    public boolean isRoomBanned(Long roomId, UUID userId) {
        return roomBanRepository.existsByRoomIdAndUserId(roomId, userId);
    }

    /** Per-room ban OR the room's owner has this user on their personal blacklist — see banFromRoom's doc comment. */
    public boolean isBlockedFromRoom(Long roomId, UUID userId) {
        if (isRoomBanned(roomId, userId)) return true;
        return roomRepository.findById(roomId)
                .map(Room::getOwnerId)
                .filter(ownerId -> userBlockRepository.existsByBlockerIdAndBlockedId(ownerId, userId))
                .isPresent();
    }

    @Transactional
    public void sendPrivateMessage(UUID fromUserId, String fromUsername, Long roomId, UUID toUserId, String text) {
        if (userBlockRepository.existsBetweenEitherDirection(fromUserId, toUserId)) {
            throw new IllegalArgumentException("Vous ne pouvez pas contacter ce toon.");
        }

        // Presence-only check — recipient must currently be in the same room (no offline delivery, see plan).
        roomStateService.getUser(roomId.toString(), toUserId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Ce joueur n'est plus dans cette room"));

        privateMessageRepository.save(PrivateMessage.builder()
                .roomId(roomId)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .message(text)
                .build());

        PrivateMessageEvent event = PrivateMessageEvent.builder()
                .fromUserId(fromUserId.toString())
                .fromUsername(fromUsername)
                .toUserId(toUserId.toString())
                .text(text)
                .sentAt(OffsetDateTime.now().toString())
                .build();

        // Delivered to both parties — the sender is just another subscriber of
        // their own queue, same principle as the equip-clothing broadcast this
        // session: no separate optimistic render, the echo is the confirmation.
        messaging.convertAndSendToUser(toUserId.toString(), "/queue/private-message", event);
        messaging.convertAndSendToUser(fromUserId.toString(), "/queue/private-message", event);
    }

    /** Called from InternalController (game-api → game-server-java) on a site-wide ban. */
    public void kickForSiteBan(UUID targetUserId, String reason) {
        var sessionId = roomStateService.getSessionIdForUser(targetUserId.toString());
        if (sessionId.isEmpty()) return; // not currently connected — login/CONNECT-time checks cover it

        var roomId = roomStateService.getRoomIdForSession(sessionId.get());
        messaging.convertAndSend(
                "/queue/kicked-user" + sessionId.get(),
                new KickedEvent("SITE_BANNED", reason));

        roomId.ifPresent(rid -> {
            roomStateService.leave(rid, targetUserId.toString());
            messaging.convertAndSend(
                    "/topic/room/" + rid + "/left",
                    UserLeftEvent.builder().userId(targetUserId.toString()).build());
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Room assertCanManageRoom(UUID actorId, int actorRank, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room introuvable"));
        if (!roomAccessService.canManageRoom(room, actorId, actorRank)) {
            throw new IllegalArgumentException("Vous ne pouvez modérer que vos propres rooms");
        }
        return room;
    }

    /** Actually removes the target from the room's live state and notifies both them and the room. */
    private void doKick(Long roomId, UUID targetUserId, String code, String message) {
        ConnectedUser target = roomStateService.getUser(roomId.toString(), targetUserId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Ce joueur n'est pas dans cette room"));

        messaging.convertAndSend(
                "/queue/kicked-user" + target.getSessionId(),
                new KickedEvent(code, message));

        roomStateService.leave(roomId.toString(), targetUserId.toString());
        messaging.convertAndSend(
                "/topic/room/" + roomId + "/left",
                UserLeftEvent.builder().userId(targetUserId.toString()).build());
    }
}
