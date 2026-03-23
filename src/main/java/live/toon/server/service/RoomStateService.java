package live.toon.server.service;

import live.toon.server.dto.event.RoomStateEvent;
import live.toon.server.entity.Room;
import live.toon.server.entity.User;
import live.toon.server.model.ConnectedUser;
import live.toon.server.model.UserPrincipal;
import live.toon.server.repository.RoomRepository;
import live.toon.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory room registry.
 * Thread-safe via ConcurrentHashMap; per-room maps are synchronised on the room-level lock.
 * Presence (online flag, current_room_id, user_count) is persisted in the shared DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomStateService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    /**
     * roomId → (userId → ConnectedUser)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConnectedUser>> rooms =
            new ConcurrentHashMap<>();

    /**
     * sessionId → (roomId, userId) — for disconnect cleanup
     */
    private final ConcurrentHashMap<String, RoomMembership> sessionIndex = new ConcurrentHashMap<>();

    /**
     * userId → sessionId — for duplicate-session detection and kick.
     */
    private final ConcurrentHashMap<String, String> userSessionIndex = new ConcurrentHashMap<>();

    // ── Startup reset ─────────────────────────────────────────────────────────

    /**
     * On startup, reset all presence data to avoid stale state after a crash.
     */
    @PostConstruct
    @Transactional
    public void resetPresenceOnStartup() {
        log.info("Resetting presence data on startup...");
        rooms.clear();
        sessionIndex.clear();
        userSessionIndex.clear();
        roomRepository.findAll().forEach(room -> { room.setUserCount(0); roomRepository.save(room); });
        userRepository.findAll().forEach(user -> { user.setOnline(false); user.setCurrentRoomId(null); userRepository.save(user); });
        log.info("Presence data reset complete.");
    }

    // ── Join / Leave ─────────────────────────────────────────────────────────

    /**
     * Add user to an in-memory room and persist presence.
     * If the user already has an active session, it is evicted first (duplicate-session protection).
     *
     * @return JoinResult containing the room and the userId of the evicted old session (if any),
     *         or empty if the room doesn't exist.
     */
    @Transactional
    public Optional<JoinResult> join(String roomId, String sessionId, UserPrincipal principal,
                                     Object avatarOptions, double x, double y) {
        Optional<Room> roomOpt = roomRepository.findById(Long.parseLong(roomId));
        if (roomOpt.isEmpty()) return Optional.empty();

        String userId = principal.getUserId().toString();

        // ── Evict existing session if the user is already connected ──────────
        String evictedSessionId = null;
        String existingSession = userSessionIndex.get(userId);
        if (existingSession != null && !existingSession.equals(sessionId)) {
            RoomMembership oldMembership = sessionIndex.remove(existingSession);
            if (oldMembership != null) {
                var oldRoomUsers = rooms.get(oldMembership.roomId());
                if (oldRoomUsers != null) oldRoomUsers.remove(userId);
                // Decrement old room and mark offline; new join will set online=true again.
                persistLeave(oldMembership.roomId(), userId);
                log.info("Evicted existing session {} of user {} from room {}",
                        existingSession, principal.getUsername(), oldMembership.roomId());
            }
            evictedSessionId = existingSession;
        }

        // ── Fetch fresh profile data from DB ─────────────────────────────────
        User dbUser = userRepository.findById(principal.getUserId()).orElse(null);
        String gender    = dbUser != null ? dbUser.getGender()      : principal.getGender();
        int    rank      = dbUser != null ? dbUser.getRank()        : principal.getRank();
        int    toonizLvl = dbUser != null ? dbUser.getToonizLevel() : principal.getToonizLevel();

        rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(userId, ConnectedUser.builder()
                        .userId(principal.getUserId())
                        .username(principal.getUsername())
                        .sessionId(sessionId)
                        .avatarOptionsJson(avatarOptions != null ? avatarOptions.toString() : "{}")
                        .gender(gender)
                        .rank(rank)
                        .toonizLevel(toonizLvl)
                        .x(x)
                        .y(y)
                        .direction(0)
                        .build());

        sessionIndex.put(sessionId, new RoomMembership(roomId, userId));
        userSessionIndex.put(userId, sessionId);
        log.info("User {} joined room {}", principal.getUsername(), roomId);

        // Persist presence + last login
        if (dbUser != null) {
            dbUser.setOnline(true);
            dbUser.setCurrentRoomId(Long.parseLong(roomId));
            dbUser.setLastLoginAt(java.time.OffsetDateTime.now());
            userRepository.save(dbUser);
        }
        Room room = roomOpt.get();
        room.setUserCount(Math.max(0, room.getUserCount() + 1));
        roomRepository.save(room);

        return Optional.of(new JoinResult(room, evictedSessionId));
    }

    public void updatePosition(String roomId, String userId, double x, double y, int direction) {
        Optional.ofNullable(rooms.get(roomId))
                .map(r -> r.get(userId))
                .ifPresent(u -> { u.setX(x); u.setY(y); u.setDirection(direction); });
    }

    /**
     * Remove user from room and persist presence. Returns the removed user if found.
     */
    @Transactional
    public Optional<ConnectedUser> leave(String roomId, String userId) {
        var roomUsers = rooms.get(roomId);
        if (roomUsers == null) return Optional.empty();
        ConnectedUser user = roomUsers.remove(userId);
        if (user != null) {
            sessionIndex.remove(user.getSessionId());
            userSessionIndex.remove(userId);
            persistLeave(roomId, userId);
        }
        return Optional.ofNullable(user);
    }

    /**
     * Handle STOMP session disconnect: look up session → room/user.
     */
    @Transactional
    public Optional<RoomMembership> onDisconnect(String sessionId) {
        RoomMembership membership = sessionIndex.remove(sessionId);
        if (membership == null) return Optional.empty();
        var roomUsers = rooms.get(membership.roomId());
        if (roomUsers != null) roomUsers.remove(membership.userId());
        userSessionIndex.remove(membership.userId());
        persistLeave(membership.roomId(), membership.userId());
        return Optional.of(membership);
    }

    /** Persist user going offline and decrement room counter. */
    private void persistLeave(String roomId, String userId) {
        UUID userUuid;
        try { userUuid = UUID.fromString(userId); }
        catch (IllegalArgumentException e) { return; }

        userRepository.findById(userUuid).ifPresent(u -> {
            u.setOnline(false);
            u.setCurrentRoomId(null);
            userRepository.save(u);
        });
        roomRepository.findById(Long.parseLong(roomId)).ifPresent(room -> {
            room.setUserCount(Math.max(0, room.getUserCount() - 1));
            roomRepository.save(room);
        });
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<ConnectedUser> getUsers(String roomId) {
        var map = rooms.get(roomId);
        if (map == null) return List.of();
        return List.copyOf(map.values());
    }

    public Optional<ConnectedUser> getUser(String roomId, String userId) {
        return Optional.ofNullable(rooms.getOrDefault(roomId, new ConcurrentHashMap<>()).get(userId));
    }

    /** Retourne tous les utilisateurs connectés (tous salons confondus). */
    public List<ConnectedUser> getAllConnectedUsers() {
        return rooms.values().stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList());
    }

    /** Retourne les paires (roomId, ConnectedUser) pour toutes les salles. */
    public List<Map.Entry<String, ConnectedUser>> getAllConnectedUsersWithRoom() {
        return rooms.entrySet().stream()
                .flatMap(e -> e.getValue().values().stream()
                        .map(u -> Map.entry(e.getKey(), u)))
                .collect(Collectors.toList());
    }

    /** Retourne le roomId d'un utilisateur à partir de son sessionId. */
    public Optional<String> getRoomIdForSession(String sessionId) {
        return Optional.ofNullable(sessionIndex.get(sessionId)).map(RoomMembership::roomId);
    }

    public RoomStateEvent buildRoomState(Room room) {
        String roomId = room.getId().toString();
        List<RoomStateEvent.UserSnapshot> snapshots = getUsers(roomId).stream()
                .map(u -> RoomStateEvent.UserSnapshot.builder()
                        .userId(u.getUserId().toString())
                        .username(u.getUsername())
                        .avatarOptions(u.getAvatarOptionsJson())
                        .x(u.getX())
                        .y(u.getY())
                        .direction(u.getDirection())
                        .gender(u.getGender())
                        .rank(u.getRank())
                        .toonizLevel(u.getToonizLevel())
                        .build())
                .collect(Collectors.toList());

        return RoomStateEvent.builder()
                .roomId(roomId)
                .name(room.getName())
                .houseData(room.getHouseData())
                .users(snapshots)
                .build();
    }

    // ── Inner records ─────────────────────────────────────────────────────────

    public record RoomMembership(String roomId, String userId) {}

    /**
     * Result of a {@link #join} call.
     *
     * @param room           the room that was joined
     * @param evictedSessionId  STOMP session ID of the previous session that was evicted, or null if none
     */
    public record JoinResult(Room room, String evictedSessionId) {
        public boolean hasEviction() { return evictedSessionId != null; }
    }
}
