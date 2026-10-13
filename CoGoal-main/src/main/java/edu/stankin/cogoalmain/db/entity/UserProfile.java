package edu.stankin.cogoalmain.db.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity @Table(name = "user_profiles") @Getter @Setter
public class UserProfile {
    // Assigned by auth; main must use the same BIGINT, not its own sequence.
    @Id private Long id;
    @Column(name = "legacy_id", unique = true) private UUID legacyId;
    @Column(nullable = false, unique = true, length = 255) private String email;
    @Column(nullable = false, length = 32) private String username;
    @Column(name = "username_key", nullable = false, unique = true, length = 32) private String usernameKey;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "avatar_url", length = 2048) private String avatarUrl;
    @Column(nullable = false, length = 20) private String role;
    @Column(nullable = false) private int coins;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "profile_version", nullable = false) private long profileVersion;
    @Column(name = "profile_initialized", nullable = false) private boolean profileInitialized;
    @PrePersist @PreUpdate void normalizeUsername() { usernameKey = username.toLowerCase(Locale.ROOT); }
}
