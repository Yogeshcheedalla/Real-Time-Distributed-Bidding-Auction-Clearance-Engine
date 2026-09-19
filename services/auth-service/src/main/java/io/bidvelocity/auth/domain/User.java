package io.bidvelocity.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) private String email;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false) private String provider = "LOCAL";
    @Column(name = "provider_id") private String providerId;
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @PreUpdate void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getEmail() { return email; } public void setEmail(String v) { email = v; }
    public String getFirstName() { return firstName; } public void setFirstName(String v) { firstName = v; }
    public String getLastName() { return lastName; } public void setLastName(String v) { lastName = v; }
    public String getPasswordHash() { return passwordHash; } public void setPasswordHash(String v) { passwordHash = v; }
    public String getProvider() { return provider; } public void setProvider(String v) { provider = v; }
    public String getProviderId() { return providerId; } public void setProviderId(String v) { providerId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { return roles; } public void setRoles(Set<Role> v) { roles = v; }
}
