package edu.stankin.cogoalmain.db.entity;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.UUID;

/** Read-only source for preserving balances and creation dates during UUID to BIGINT migration. */
@Entity @Immutable @Table(name = "legacy_user_profiles") @Getter
public class LegacyUserProfile {
    @Id private UUID id;
    private int coins;
    @Column(name = "created_at") private Instant createdAt;
}
