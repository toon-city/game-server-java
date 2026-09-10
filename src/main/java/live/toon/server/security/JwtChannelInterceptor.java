package live.toon.server.security;

import live.toon.server.entity.User;
import live.toon.server.model.UserPrincipal;
import live.toon.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT rejected: missing or malformed Authorization header");
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtService.isValid(token)) {
            // Tip for developers: if both servers are running but this fires on every
            // connect, the JWT_SECRET env vars probably differ between game-api and
            // game-server-java.  Use 'make dev-server' or set JWT_SECRET explicitly.
            log.warn("STOMP CONNECT rejected: JWT validation failed "
                    + "(check that JWT_SECRET matches between game-api and game-server-java)");
            throw new IllegalArgumentException("Invalid JWT token");
        }

        UserPrincipal principal = jwtService.extractPrincipal(token);

        // Deliberate deviation from "no DB round-trip at CONNECT" (see the
        // rest of this class): a JWT minted before a ban is still perfectly
        // valid by signature alone, and a site ban is supposed to be a real
        // wall, not just something that stops new logins. This is the only
        // place that re-checks live DB state instead of trusting the token.
        User dbUser = userRepository.findById(principal.getUserId()).orElse(null);
        if (dbUser != null && dbUser.isBanned()) {
            OffsetDateTime until = dbUser.getBannedUntil();
            boolean expired = until != null && until.isBefore(OffsetDateTime.now());
            if (!expired) {
                log.warn("STOMP CONNECT rejected: user {} is banned", principal.getUserId());
                throw new IllegalArgumentException("Compte banni");
            }
            // Expired temp ban: game-api's own login flow owns clearing the flag —
            // this path only decides whether to let the CONNECT through today.
        }

        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        accessor.setUser(auth);

        log.debug("STOMP CONNECT authenticated: userId={} username={}", principal.getUserId(), principal.getUsername());
        return message;
    }
}
