package live.toon.server.dto.event;

/**
 * Sent to /queue/kicked-user{sessionId} — a session-specific internal
 * destination, not a broadcast (the target has the same userId as anyone
 * else who might be subscribed to their own /user/queue/*, so this has to
 * be addressed by session, not by user).
 *
 * code distinguishes why the client should react differently:
 * DUPLICATE_SESSION / ROOM_KICKED / ROOM_BANNED → back to the lobby.
 * SITE_BANNED → full logout, the JWT itself is no longer honoured going
 * forward (see JwtChannelInterceptor).
 */
public record KickedEvent(String code, String message) {}
