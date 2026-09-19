package io.bidvelocity.auth.service;

import io.bidvelocity.auth.repo.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reuse detection must persist even though the caller throws right after:
 * runs in its own committed transaction (REQUIRES_NEW), never rolled back by
 * the failed refresh attempt. A stolen-then-reused token kills the family.
 */
@Component
public class RefreshReuseGuard {

    private final RefreshTokenRepository tokens;

    public RefreshReuseGuard(RefreshTokenRepository tokens) { this.tokens = tokens; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId) {
        tokens.revokeFamily(familyId);
    }
}
