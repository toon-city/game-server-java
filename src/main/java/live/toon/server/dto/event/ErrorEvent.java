package live.toon.server.dto.event;

/** Sent to /user/queue/error when a client action is rejected server-side. */
public record ErrorEvent(String code, String message) {}
