package io.bidvelocity.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Issues and verifies the application JWT (userId, email, roles, iat, expiration). */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(@Value("${bidvelocity.jwt.secret}") String secret,
                      @Value("${bidvelocity.jwt.access-minutes:120}") long accessMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("bidvelocity.jwt.secret must be >= 32 bytes — set JWT_SECRET in .env");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(accessMinutes);
    }

    public String issueAccess(User u) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(u.id()))
                .claim("email", u.email())
                .claim("roles", u.roles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** @return verified claims, or empty on ANY failure (tamper, expiry, wrong alg). */
    public Claims verify(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long accessTtlSeconds() { return accessTtl.toSeconds(); }

    /** minimal user view for token issuance — avoids leaking entities into the JWT layer */
    public record User(long id, String email, List<String> roles) {}
}
