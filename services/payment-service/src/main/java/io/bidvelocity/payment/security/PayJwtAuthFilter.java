package io.bidvelocity.payment.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Verify-only JWT (the payment service never issues tokens). */
@Component
public class PayJwtAuthFilter extends OncePerRequestFilter {

    public record Principal(long id, List<String> roles) {}

    private final SecretKey key;

    public PayJwtAuthFilter(@Value("${bidvelocity.jwt.secret}") String secret) {
        byte[] b = secret.getBytes(StandardCharsets.UTF_8);
        if (b.length < 32) throw new IllegalStateException("jwt secret >= 32 bytes");
        this.key = Keys.hmacShaKeyFor(b);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(h.substring(7)).getPayload();
                @SuppressWarnings("unchecked")
                List<String> roles = c.get("roles", List.class);
                var auths = (roles == null ? List.<String>of() : roles).stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        new Principal(Long.parseLong(c.getSubject()), roles == null ? List.of() : roles), h, auths));
            } catch (JwtException | IllegalArgumentException ignored) { /* anonymous → entry point 401s protected routes */ }
        }
        chain.doFilter(req, res);
    }
}
