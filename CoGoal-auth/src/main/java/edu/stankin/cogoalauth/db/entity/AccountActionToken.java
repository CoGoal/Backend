package edu.stankin.cogoalauth.db.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="auth_tokens") @Getter @Setter
@org.hibernate.annotations.SQLRestriction("type IN ('PASSWORD_RESET', 'EMAIL_CONFIRM')")
public class AccountActionToken extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @Column(nullable=false,length=20) private String type;
    @Column(name="new_email",length=255) private String newEmail;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="used_at") private Instant usedAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
}
