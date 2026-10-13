package edu.stankin.cogoalmain.db.repo;
import edu.stankin.cogoalmain.db.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {}
