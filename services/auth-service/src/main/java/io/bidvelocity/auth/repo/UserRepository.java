package io.bidvelocity.auth.repo;

import io.bidvelocity.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
