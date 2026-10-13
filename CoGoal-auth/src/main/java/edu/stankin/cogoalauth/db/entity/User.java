package edu.stankin.cogoalauth.db.entity;

import edu.stankin.cogoalauth.db.entity.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "legacy_id", unique = true)
    private UUID legacyId;
    @Column(nullable = false, length = 255)
    private String email;
    @Column(nullable = false, length = 32)
    private String username;
    @Column(name = "profile_version", nullable = false)
    private long profileVersion;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Role role = Role.USER;
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Keep the existing roles claim/API while persisting the canonical single role.
    public Set<Role> getRoles() {
        return role == Role.ADMIN ? EnumSet.of(Role.USER, Role.ADMIN) : EnumSet.of(Role.USER);
    }
    public void setRoles(Set<Role> roles) {
        role = roles.contains(Role.ADMIN) ? Role.ADMIN : Role.USER;
    }
    public boolean isLockedAt(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }
    @Override public String toString() { return "User(id=" + id + ", role=" + role + ")"; }
}
