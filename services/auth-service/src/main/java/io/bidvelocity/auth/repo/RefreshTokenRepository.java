package io.bidvelocity.auth.repo;

import io.bidvelocity.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.familyId = :family AND t.revoked = false")
    int revokeFamily(UUID family);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllForUser(Long userId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
