package edu.stankin.cogoalauth.db.repo;

import edu.stankin.cogoalauth.db.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Locks the token row: two concurrent refreshes with the same token run one after another,
     * so the second one sees the token already revoked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.type = 'REFRESH' and t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.type = 'REFRESH' and t.user.id = :userId and t.revokedAt is null")
    int revokeAllOfUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.type = 'REFRESH' and t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
