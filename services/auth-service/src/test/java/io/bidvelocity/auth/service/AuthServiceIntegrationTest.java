package io.bidvelocity.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.bidvelocity.auth.domain.User;
import io.bidvelocity.auth.repo.RefreshTokenRepository;
import io.bidvelocity.auth.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real-stack integration tests (full Spring context + security + JPA on
 * in-memory H2 in PostgreSQL mode). Mirrors the guarantees verified in the
 * BidVelocity demo: JWT issue/verify/tamper, BCrypt-only persistence,
 * refresh rotation with reuse detection, suspension cutting sessions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "MERGE INTO roles(name) KEY(name) VALUES ('USER'),('SELLER'),('ADMIN')",
        "DELETE FROM user_roles", "DELETE FROM refresh_tokens", "DELETE FROM users"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AuthServiceIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthService auth;

    private String register(String email, String pw) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"firstName":"Yog","lastName":"C","email":"%s","password":"%s","role":"USER","termsAccepted":true}
                          """.formatted(email, pw)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();
        return r.getResponse().getContentAsString();
    }

    @Test
    void registerStoresBcryptHashNeverPlaintext() throws Exception {
        register("y@c.io", "Str0ngPassw0rd!");
        User u = users.findByEmailIgnoreCase("y@c.io").orElseThrow();
        assertThat(u.getPasswordHash()).startsWith("$2");                 // BCrypt
        assertThat(u.getPasswordHash()).doesNotContain("Str0ngPassw0rd!");
        assertThat(u.getRoles()).extracting("name").containsExactly("USER");
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        register("dup@c.io", "Str0ngPassw0rd!");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"firstName":"Dee","lastName":"U","email":"dup@c.io","password":"Str0ngPassw0rd!","termsAccepted":true}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void weakPasswordAndMissingTermsAre400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"firstName":"W","lastName":"P","email":"w@c.io","password":"short","termsAccepted":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void adminRoleCannotBeSelfAssigned() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"firstName":"Sam","lastName":"Neak","email":"sneak@c.io","password":"Str0ngPassw0rd!","role":"ADMIN","termsAccepted":true}"""))
                .andExpect(status().isCreated());
        assertThat(users.findByEmailIgnoreCase("sneak@c.io").orElseThrow().getRoles())
                .extracting("name").doesNotContain("ADMIN").containsExactly("USER");
    }

    @Test
    void loginRoundTripAndMeEndpoint() throws Exception {
        register("me@c.io", "Str0ngPassw0rd!");
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"email":"me@c.io","password":"Str0ngPassw0rd!"}"""))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
        assertThat(token.split("\\.")).hasSize(3);

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@c.io"));

        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token + "TAMPERED"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownUserBoth401sameShape() throws Exception {
        register("pw@c.io", "Str0ngPassw0rd!");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"email":"pw@c.io","password":"WrongPassword123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"email":"ghost@c.io","password":"Whatever123!"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshRotationAndReuseDetectionRevokesFamily() throws Exception {
        JsonNode first = json.readTree(register("rot@c.io", "Str0ngPassw0rd!"));
        String rt1 = first.get("refreshToken").asText();

        MvcResult rotated = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rt1 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();
        String rt2 = json.readTree(rotated.getResponse().getContentAsString()).get("refreshToken").asText();
        assertThat(rt2).isNotEqualTo(rt1);

        // reusing the rotated-away token must fail AND revoke the whole family
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rt1 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rt2 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspensionBlocksLoginAndCutsSessions() throws Exception {
        JsonNode reg = json.readTree(register("susp@c.io", "Str0ngPassw0rd!"));
        long id = reg.get("user").get("id").asLong();
        String rt = reg.get("refreshToken").asText();

        auth.setStatus(id, "SUSPENDED");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"email":"susp@c.io","password":"Str0ngPassw0rd!"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_SUSPENDED"));
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rt + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLoginUpsertsUserOncePerGoogleSubject() {
        User created = auth.upsertGoogleUser("geo@gmail.com", "Geo", "Trail", "google-sub-123");
        assertThat(created.getId()).isNotNull();
        assertThat(created.getProvider()).isEqualTo("GOOGLE");
        assertThat(created.getRoles()).extracting("name").containsExactly("USER");

        User again = auth.upsertGoogleUser("geo@gmail.com", "Geo", "Trail", "google-sub-123");
        assertThat(again.getId()).isEqualTo(created.getId());
        assertThat(users.findByEmailIgnoreCase("geo@gmail.com")).isPresent();
    }

    @Test
    void refreshTokenStoredHashedNotRaw() throws Exception {
        String body = register("hash@c.io", "Str0ngPassw0rd!");
        String rt = json.readTree(body).get("refreshToken").asText();
        assertThat(refreshTokens.findAll()).isNotEmpty();
        assertThat(refreshTokens.findAll().stream().noneMatch(t -> t.getTokenHash().equals(rt))).isTrue();
    }
}
