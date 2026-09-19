package io.bidvelocity.bidding.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class JwtService {
    private final javax.crypto.SecretKey key;
    public JwtService(@Value("${bidvelocity.jwt.secret}") String secret) {
        byte[] b = secret.getBytes(StandardCharsets.UTF_8);
        if (b.length < 32) throw new IllegalStateException("jwt secret >= 32 bytes");
        this.key = Keys.hmacShaKeyFor(b);
    }
    public Claims verify(String token) {
        try { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
        catch (JwtException | IllegalArgumentException e) { return null; }
    }
    @SuppressWarnings("unchecked")
    public static List<String> roles(Claims c) { return c.get("roles", List.class); }
}
