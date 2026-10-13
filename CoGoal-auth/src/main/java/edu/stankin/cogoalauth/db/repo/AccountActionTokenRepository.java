package edu.stankin.cogoalauth.db.repo;
import edu.stankin.cogoalauth.db.entity.AccountActionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface AccountActionTokenRepository extends JpaRepository<AccountActionToken,Long> {
    Optional<AccountActionToken> findByTokenHashAndType(String hash,String type);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AccountActionToken t where t.tokenHash=:hash and t.type=:type")
    Optional<AccountActionToken> lock(@Param("hash") String hash,@Param("type") String type);
    @Modifying(flushAutomatically=true)
    @Query("update AccountActionToken t set t.usedAt=:now where t.user.id=:userId and t.type=:type and t.usedAt is null")
    int consumeAll(@Param("userId") Long userId,@Param("type") String type,@Param("now") Instant now);
}
