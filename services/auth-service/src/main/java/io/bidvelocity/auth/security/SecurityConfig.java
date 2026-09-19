package io.bidvelocity.auth.security;

import io.bidvelocity.auth.oauth.GoogleLoginSuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final GoogleLoginSuccessHandler googleLoginSuccessHandler;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, GoogleLoginSuccessHandler googleLoginSuccessHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.googleLoginSuccessHandler = googleLoginSuccessHandler;
    }

    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless JWT API; no cookie auth for the JSON surface
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.frameOptions(f -> f.deny()).contentTypeOptions(c -> {}))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**", "/actuator/health", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/**").authenticated()
                .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .oauth2Login(o -> o.successHandler(googleLoginSuccessHandler))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    public static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
