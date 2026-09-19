package io.bidvelocity.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean revoked = false;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
    public String getTokenHash() { return tokenHash; } public void setTokenHash(String v) { tokenHash = v; }
    public UUID getFamilyId() { return familyId; } public void setFamilyId(UUID v) { familyId = v; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant v) { expiresAt = v; }
    public boolean isRevoked() { return revoked; } public void setRevoked(boolean v) { revoked = v; }
    public Instant getCreatedAt() { return createdAt; }
}
