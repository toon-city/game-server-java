package live.toon.server.controller;

import live.toon.server.service.RoomModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal-only, called exclusively by game-api (GameServerClient) — never
 * by any browser client. SecurityConfig's permitAll() covers this path like
 * every other HTTP endpoint here, so the guard is manual: every method
 * checks X-Internal-Secret itself before doing anything, rather than
 * introducing a second Spring Security filter chain for one endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final RoomModerationService roomModerationService;

    @Value("${internal.secret}")
    private String internalSecret;

    @PostMapping("/users/{id}/kick")
    public ResponseEntity<Void> kick(
            @PathVariable UUID id,
            @RequestBody(required = false) KickBody body,
            @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret) {
        if (providedSecret == null || !providedSecret.equals(internalSecret)) {
            log.warn("Rejected /internal/users/{}/kick: missing or wrong X-Internal-Secret", id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String reason = body != null ? body.reason() : null;
        roomModerationService.kickForSiteBan(id, reason != null ? reason : "Compte banni.");
        return ResponseEntity.ok().build();
    }

    record KickBody(String reason) {}
}
