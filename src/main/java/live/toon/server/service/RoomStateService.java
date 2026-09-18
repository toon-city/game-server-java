package live.toon.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.toon.server.dto.event.RoomStateEvent;
import live.toon.server.entity.Item;
import live.toon.server.entity.Metier;
import live.toon.server.entity.Room;
import live.toon.server.entity.User;
import live.toon.server.entity.UserItem;
import live.toon.server.model.ConnectedUser;
import live.toon.server.model.UserPrincipal;
import live.toon.server.repository.RoomRepository;
import live.toon.server.repository.UserItemRepository;
import live.toon.server.repository.UserRepository;
import live.toon.server.util.HouseGeometry;
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
    private final UserItemRepository userItemRepository;
    private final FurnitureStateService furnitureStateService;
    private final TextureStateService textureStateService;
    private final RoomAccessService roomAccessService;
    private final ObjectMapper objectMapper;

    /** Mirrors game-types' RoomPermission.OWN / .VIEW ordinals. */
    private static final int PERMISSION_OWN = 2;
    private static final int PERMISSION_VIEW = 0;

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
                                     int direction, double x, double y) {
        Optional<Room> roomOpt = roomRepository.findById(Long.parseLong(roomId));
        if (roomOpt.isEmpty()) return Optional.empty();

        String userId = principal.getUserId().toString();

        // ── Evict existing session if the user is already connected ──────────
        String evictedSessionId = null;
        String evictedRoomId = null;
        String existingSession = userSessionIndex.get(userId);
        if (existingSession != null && !existingSession.equals(sessionId)) {
            RoomMembership oldMembership = sessionIndex.remove(existingSession);
            if (oldMembership != null) {
                var oldRoomUsers = rooms.get(oldMembership.roomId());
                // Locked like every other membership change so it can't land in
                // the middle of another joiner's snapshot. Released before the
                // new room is locked below — the two are never held at once.
                if (oldRoomUsers != null) {
                    synchronized (oldRoomUsers) {
                        oldRoomUsers.remove(userId);
                    }
                }
                // Decrement old room and mark offline; new join will set online=true again.
                persistLeave(oldMembership.roomId(), userId);
                // Only the old room needs a "left" broadcast if it differs from the one being joined
                // (rejoining the same room, e.g. tab refresh, doesn't leave anyone behind to notify).
                if (!oldMembership.roomId().equals(roomId)) {
                    evictedRoomId = oldMembership.roomId();
                }
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
        int    skinColor = dbUser != null && dbUser.getSkinColor() != null ? dbUser.getSkinColor() : 0xf7ceaf;
        int    hairColor = dbUser != null && dbUser.getHairColor() != null ? dbUser.getHairColor() : 0xffffff;

        // ── Charger les vêtements équipés depuis la DB ────────────────────────
        Map<String, String> clothing = buildClothingMap(principal.getUserId());

        ConcurrentHashMap<String, ConnectedUser> roomUsers =
                rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        Room room = roomOpt.get();

        // Spawn at the room's own door, not wherever the client claims to be —
        // there's no "last known position" yet for a fresh join, and trusting a
        // self-reported x/y here let a client claim to spawn anywhere, including
        // outside the room's walls entirely. Falls back to the client-supplied
        // x/y only if house_data has no parseable entry door (e.g. a room still
        // missing a layout) rather than failing the join outright.
        var doorCenter = HouseGeometry.findDoorCenter(room.getHouseData(), objectMapper);
        double spawnX = doorCenter.map(HouseGeometry.Point::x).orElse(x);
        double spawnY = doorCenter.map(HouseGeometry.Point::y).orElse(y);

        RoomStateEvent roomState;

        // Register the joiner and snapshot the room under the same lock. When two
        // players join at the same instant, each one then either sees the other
        // in its own snapshot, or registered first and is therefore announced to
        // the other by the "joined" broadcast. Taking the snapshot outside the
        // lock allowed both snapshots to be built before either insert landed,
        // leaving two players in the same room invisible to each other.
        synchronized (roomUsers) {
            roomUsers.put(userId, ConnectedUser.builder()
                    .userId(principal.getUserId())
                    .username(principal.getUsername())
                    .sessionId(sessionId)
                    .skinColor(skinColor)
                    .hairColor(hairColor)
                    .clothing(clothing)
                    .gender(gender)
                    .rank(rank)
                    .toonizLevel(toonizLvl)
                    .x(spawnX)
                    .y(spawnY)
                    .direction(direction)
                    .build());
            roomState = snapshotRoom(room, roomUsers.values(), principal.getUserId(), rank);
        }

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
        room.setUserCount(Math.max(0, room.getUserCount() + 1));
        roomRepository.save(room);

        return Optional.of(new JoinResult(room, roomState, evictedSessionId, evictedRoomId));
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
        ConnectedUser user;
        synchronized (roomUsers) {
            user = roomUsers.remove(userId);
        }
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
        if (roomUsers != null) {
            synchronized (roomUsers) {
                roomUsers.remove(membership.userId());
            }
        }
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

    /**
     * Retourne la session STOMP active d'un utilisateur, peu importe la room —
     * utilisé pour le kick site-wide (RoomModerationService.kickForSiteBan),
     * où la cible peut être dans n'importe quelle room ou même dans aucune.
     */
    public Optional<String> getSessionIdForUser(String userId) {
        return Optional.ofNullable(userSessionIndex.get(userId));
    }

    public RoomStateEvent buildRoomState(Room room, UUID viewerUserId, int viewerRank) {
        return snapshotRoom(room, getUsers(room.getId().toString()), viewerUserId, viewerRank);
    }

    /**
     * Build the room state from an explicit user list (callers may hold the room lock).
     * yourPermission is computed for viewerUserId specifically — this event is only
     * ever sent to that one user (/user/queue/state), never broadcast, so it's safe
     * for it to carry a per-viewer field.
     */
    private RoomStateEvent snapshotRoom(Room room, Collection<ConnectedUser> users,
                                         UUID viewerUserId, int viewerRank) {
        String roomId = room.getId().toString();
        List<RoomStateEvent.UserSnapshot> snapshots = users.stream()
                .map(u -> RoomStateEvent.UserSnapshot.builder()
                        .userId(u.getUserId().toString())
                        .username(u.getUsername())
                        .skinColor(u.getSkinColor())
                        .hairColor(u.getHairColor())
                        .clothing(u.getClothing())
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
                .furnitures(furnitureStateService.listPlaced(room.getId()))
                .textures(textureStateService.listApplied(room.getId()))
                .yourPermission(computePermission(room, viewerUserId, viewerRank))
                .build();
    }

    /**
     * Delegates to RoomAccessService.canManageRoom() (the room's owner, or any
     * admin — moderators get nothing extra here). No intermediate co-editor
     * tier exists yet, so this is binary: OWN or VIEW.
     */
    private int computePermission(Room room, UUID viewerUserId, int viewerRank) {
        return roomAccessService.canManageRoom(room, viewerUserId, viewerRank) ? PERMISSION_OWN : PERMISSION_VIEW;
    }

    // ── Clothing helpers ──────────────────────────────────────────────────────

    /**
     * Charge les vêtements équipés depuis la DB et retourne la map slot → sprite.
     * Ex : { "hair" → "hair7", "hat" → "hat_april1" }
     *
     * Applies the same "tenue de travail" overlay as game-api's
     * WorkOutfitService (that class can't be shared across services, so the
     * rule is duplicated here — keep both in sync): when the user's
     * work_outfit_active flag is set and they have a métier, tshirt/pant/hat
     * come from the métier's outfit instead of whatever's actually equipped
     * (hair is the only equipped category kept) — a real overlay over the
     * DB state, not an unequip, so it never touches UserItem rows.
     */
    public Map<String, String> buildClothingMap(UUID userId) {
        Map<String, String> equipped = userItemRepository
                .findByUserIdAndEquippedTrueAndItemItemType(userId, "CLOTHING")
                .stream()
                .filter(ui -> ui.getItem().getSpriteKey() != null && ui.getItem().getSpritePath() != null)
                .collect(Collectors.toMap(
                        ui -> ui.getItem().getSpriteKey(),
                        ui -> ui.getItem().getSpritePath(),
                        (a, b) -> a // en cas de doublon garder le premier
                ));

        User user = userRepository.findById(userId).orElse(null);
        Metier metier = user != null ? user.getMetier() : null;
        if (user == null || !user.isWorkOutfitActive() || metier == null) {
            return equipped;
        }

        Map<String, String> overlaid = new HashMap<>();
        String hair = equipped.get("hair");
        if (hair != null) overlaid.put("hair", hair);
        putIfSprited(overlaid, metier.getOutfitTshirt());
        putIfSprited(overlaid, metier.getOutfitPant());
        putIfSprited(overlaid, metier.getOutfitHat());
        return overlaid;
    }

    private void putIfSprited(Map<String, String> map, Item item) {
        if (item != null && item.getSpriteKey() != null && item.getSpritePath() != null) {
            map.put(item.getSpriteKey(), item.getSpritePath());
        }
    }

    /**
     * Re-charge les vêtements équipés depuis la DB pour un utilisateur dans une room
     * et met à jour son ConnectedUser en mémoire.
     * Retourne le ConnectedUser mis à jour, ou empty si l'utilisateur n'est pas dans cette room.
     *
     * @Transactional is required here: buildClothingMap() reads UserItem.item, a
     * LAZY @ManyToOne, and accesses it (getSpriteKey/getSpritePath) inside the
     * stream — without an open Hibernate session that throws
     * LazyInitializationException ("no Session") the moment the proxy is
     * touched. join() calling the same buildClothingMap() never hit this
     * because join() is itself @Transactional; this STOMP-triggered path
     * (RoomStompController.clothingRefresh -> refreshClothing) had no
     * transaction of its own. Confirmed live: equipping an item and
     * refreshing clothing over a real STOMP connection threw exactly this,
     * so the broadcast never went out — nobody's avatar-appearance update
     * ever reached the room.
     */
    @Transactional(readOnly = true)
    public Optional<ConnectedUser> refreshClothing(String roomId, String userId) {
        var roomUsers = rooms.get(roomId);
        if (roomUsers == null) return Optional.empty();
        ConnectedUser cu = roomUsers.get(userId);
        if (cu == null) return Optional.empty();
        try {
            UUID userUuid = UUID.fromString(userId);
            Map<String, String> clothing = buildClothingMap(userUuid);
            cu.setClothing(clothing);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId in refreshClothing: {}", userId);
        }
        return Optional.of(cu);
    }

    // ── Inner records ─────────────────────────────────────────────────────────

    public record RoomMembership(String roomId, String userId) {}

    /**
     * Result of a {@link #join} call.
     *
     * @param room             the room that was joined
     * @param roomState        snapshot taken atomically with the join, to send back to the joiner
     * @param evictedSessionId STOMP session ID of the previous session that was evicted, or null if none
     * @param evictedRoomId    room the evicted session was in, or null if none/same room as the new join
     */
    public record JoinResult(Room room, RoomStateEvent roomState,
                             String evictedSessionId, String evictedRoomId) {
        public boolean hasEviction() { return evictedSessionId != null; }
    }
}
