package io.bidvelocity.auth.config;

import io.bidvelocity.auth.domain.Role;
import io.bidvelocity.auth.domain.User;
import io.bidvelocity.auth.repo.RoleRepository;
import io.bidvelocity.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Development-only demo accounts (spec §65). Idempotent: runs only when the
 * users table is empty and bidvelocity.demo.seed=true. Credentials are
 * documented in the README as DEV-ONLY. Disabled in tests unless enabled
 * explicitly.
 */
@Component
@ConditionalOnProperty(name = "bidvelocity.demo.seed", havingValue = "true")
public class DemoUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public DemoUserSeeder(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users; this.roles = roles; this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;
        users.save(user("admin@bidvelocity.io", "Aarav", "Admin", "Admin@123", "ADMIN"));
        users.save(user("seller@bidvelocity.io", "Kavya", "Collections", "Seller@123", "SELLER"));
        users.save(user("meera@bidvelocity.io", "Meera", "Emporium", "Seller@123", "SELLER"));
        users.save(user("riya@bidvelocity.io", "Riya", "Sharma", "Bidder@123", "USER"));
        users.save(user("vikram@bidvelocity.io", "Vikram", "Nair", "Bidder@123", "USER"));
        users.save(user("arjun@bidvelocity.io", "Arjun", "Rao", "Bidder@123", "USER"));
        log.info("demo users seeded (development-only credentials, see README)");
    }

    private User user(String email, String first, String last, String rawPw, String role) {
        User u = new User();
        u.setEmail(email); u.setFirstName(first); u.setLastName(last);
        u.setPasswordHash(encoder.encode(rawPw)); // BCrypt — never plaintext, even for demos
        u.setProvider("LOCAL"); u.setStatus("ACTIVE");
        u.setRoles(Set.of(roles.findByName(role).orElseGet(() -> roles.save(new Role(role)))));
        return u;
    }
}
