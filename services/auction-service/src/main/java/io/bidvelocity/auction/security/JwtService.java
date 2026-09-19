package io.bidvelocity.auction.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Same secret as auth-service (shared HS256 via JWT_SECRET env) — standard for a small trusted mesh. */
@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(@Value("${bidvelocity.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("bidvelocity.jwt.secret must be >= 32 bytes");
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public Claims verify(String token) {
        try { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
        catch (JwtException | IllegalArgumentException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    public static List<String> roles(Claims c) { return c.get("roles", List.class); }
}
