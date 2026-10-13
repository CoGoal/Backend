package edu.stankin.cogoalauth.db.repo;

import edu.stankin.cogoalauth.db.entity.MainProfileTask;
import edu.stankin.cogoalauth.db.entity.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MainProfileTaskRepository extends JpaRepository<MainProfileTask, UUID> {

    /**
     * Due PENDING tasks, locked with {@code SKIP LOCKED}: several instances of the service
     * take different tasks instead of waiting for each other or sending the same one.
     */
    @Query(value = """
            select * from main_profile_outbox
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<MainProfileTask> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    List<MainProfileTask> findByUserId(Long userId);

    long countByStatus(OutboxStatus status);
}
