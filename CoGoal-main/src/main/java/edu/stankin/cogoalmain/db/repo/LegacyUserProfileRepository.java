package edu.stankin.cogoalmain.db.repo;
import edu.stankin.cogoalmain.db.entity.LegacyUserProfile;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.UUID;
public interface LegacyUserProfileRepository extends Repository<LegacyUserProfile, UUID> {
    Optional<LegacyUserProfile> findById(UUID id);
}
