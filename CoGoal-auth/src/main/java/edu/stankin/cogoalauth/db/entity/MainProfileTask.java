package edu.stankin.cogoalauth.db.entity;

import edu.stankin.cogoalauth.db.entity.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox task publishing a complete account snapshot to Kafka.
 * Written in the same transaction as the user, sent later by {@code ProfileOutboxPublisher}.
 * Versioned account snapshots are delivered after registration and account changes.
 * Main ignores older versions and awards initial coins only on the first insert.
 */
@Getter
@Setter
@Entity
@Table(name = "main_profile_outbox")
public class MainProfileTask extends BaseEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "legacy_user_id")
    private UUID legacyUserId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    // Registration payload only; cleared after broker acknowledgement.
    @Column(name = "initial_name", length = 100)
    private String initialName;
    // Preserves undelivered legacy registration/migration payloads during rollout.
    @Column(name = "initial_avatar_url", length = 2048)
    private String initialAvatarUrl;
    @Column(name = "profile_version", nullable = false)
    private long profileVersion;
    @Column(name = "role", nullable = false)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
