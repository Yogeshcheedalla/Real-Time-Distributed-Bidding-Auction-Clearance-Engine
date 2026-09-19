package io.bidvelocity.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Isolated so AuthService never depends on SecurityConfig (avoids bean cycle). */
@Configuration
public class CryptoConfig {
    /** BCrypt cost 10 — passwords are NEVER stored or logged in plaintext. */
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
}
