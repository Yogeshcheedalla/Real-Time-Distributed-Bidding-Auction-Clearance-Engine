package io.bidvelocity.auth.config;

import io.bidvelocity.auth.dto.Dtos.LoginRequest;
import io.bidvelocity.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Demo accounts are seeded with real BCrypt hashes and can actually sign in. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "bidvelocity.demo.seed=true")
class DemoUserSeederTest {

    @Autowired AuthService auth;
    @Autowired DemoUserSeeder seeder;

    @BeforeEach
    void seed() { seeder.run(); }   // @SpringBootTest does not execute CommandLineRunners

    @Test
    void seededSellerLogsInWithBcryptHash() {
        var r = auth.login(new LoginRequest("seller@bidvelocity.io", "Seller@123"));
        assertThat(r.user().roles()).contains("SELLER");
        assertThat(r.accessToken().split("\\.")).hasSize(3);
    }

    @Test
    void seededAdminHasAdminRole() {
        var r = auth.login(new LoginRequest("admin@bidvelocity.io", "Admin@123"));
        assertThat(r.user().roles()).containsExactly("ADMIN");
    }
}
