package live.toon.server.security;

import io.jsonwebtoken.*;
import live.toon.server.model.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = secret.length() >= 32
                ? secret.getBytes()
                : java.util.Arrays.copyOf(secret.getBytes(), 32);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UserPrincipal extractPrincipal(String token) {
        Claims claims = parse(token);
        UUID userId = UUID.fromString(claims.getSubject());
        String username = claims.get("username", String.class);
        String gender = claims.get("gender", String.class);
        Integer rank = claims.get("rank", Integer.class);
        Integer toonizLevel = claims.get("toonizLevel", Integer.class);
        return new UserPrincipal(
                userId, username, gender,
                rank != null ? rank : 0,
                toonizLevel != null ? toonizLevel : 0);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
