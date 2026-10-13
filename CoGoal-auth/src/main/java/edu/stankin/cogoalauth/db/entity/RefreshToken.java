package edu.stankin.cogoalauth.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token, stored as the SHA-256 hash of its value.
 * <p>
 * {@code familyId} ties together all tokens obtained by rotation from one login. A revoked token that is
 * presented again means someone else has a copy, and the whole family is revoked.
 */
@Getter
@Setter
@Entity
@Table(name = "auth_tokens")
@org.hibernate.annotations.SQLRestriction("type = 'REFRESH'")
public class RefreshToken extends BaseEntity {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false, length = 20)
    private String type = "REFRESH";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
