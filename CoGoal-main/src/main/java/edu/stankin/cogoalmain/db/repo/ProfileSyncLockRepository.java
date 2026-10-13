package edu.stankin.cogoalmain.db.repo;
import edu.stankin.cogoalmain.db.entity.ProfileSyncLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
public interface ProfileSyncLockRepository extends Repository<ProfileSyncLock, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ProfileSyncLock l where l.id = :id")
    ProfileSyncLock lock(@Param("id") int id);
}
