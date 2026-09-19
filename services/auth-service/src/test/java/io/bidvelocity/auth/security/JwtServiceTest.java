package io.bidvelocity.auth.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    static final String SECRET = "unit-test-secret-value-that-is-long-enough-32";

    @Test
    void issueThenVerifyRoundTripsClaims() {
        JwtService jwt = new JwtService(SECRET, 60);
        String token = jwt.issueAccess(new JwtService.User(42L, "a@b.io", List.of("USER", "SELLER")));
        Claims c = jwt.verify(token);
        assertThat(c).isNotNull();
        assertThat(c.getSubject()).isEqualTo("42");
        assertThat(c.get("email")).isEqualTo("a@b.io");
        assertThat(c.get("roles", List.class)).containsExactlyInAnyOrder("USER", "SELLER");
    }

    @Test
    void tamperedTokenFailsVerification() {
        JwtService jwt = new JwtService(SECRET, 60);
        String token = jwt.issueAccess(new JwtService.User(1L, "a@b.io", List.of("USER")));
        String forged = token.substring(0, token.lastIndexOf('.') + 1) + "AAAA";
        assertThat(jwt.verify(forged)).isNull();
        assertThat(jwt.verify(token + "x")).isNull();
        // attacker-signed with a different key must NOT pass
        String otherKey = new JwtService("completely-different-secret-value-33333333", 60)
                .issueAccess(new JwtService.User(1L, "a@b.io", List.of("ADMIN")));
        assertThat(jwt.verify(otherKey)).isNull();
    }

    @Test
    void expiredTokenFailsVerification() throws Exception {
        JwtService jwt = new JwtService(SECRET, -1); // issued already-expired
        String token = jwt.issueAccess(new JwtService.User(1L, "a@b.io", List.of("USER")));
        assertThat(jwt.verify(token)).isNull();
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
