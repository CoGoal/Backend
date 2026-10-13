package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.kafka.event.UserProfileSnapshot;
import edu.stankin.cogoalauth.kafka.event.UserProfileEvent;
import edu.stankin.cogoalauth.config.AppProperties;
import edu.stankin.cogoalauth.db.entity.MainProfileTask;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.OutboxStatus;
import edu.stankin.cogoalauth.db.repo.MainProfileTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Outbox of "create profile in main" tasks: writing them together with the user, claiming due ones
 * and recording the outcome of each attempt. Sending itself is done by {@code ProfileOutboxPublisher}
 * outside of any transaction, so a slow broker never holds a database transaction open.
 */
@Service
@RequiredArgsConstructor
public class MainProfileOutbox {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final MainProfileTaskRepository taskRepository;
    private final AppProperties properties;
    private final Clock clock;

    /** Called in the registration transaction: the task exists if and only if the user does. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(User user) {
        enqueueSnapshot(user, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueRegistration(User user, String initialName) {
        enqueueSnapshot(user, initialName);
    }

    private void enqueueSnapshot(User user, String initialName) {
        Instant now = clock.instant();
        MainProfileTask task = new MainProfileTask();
        task.setId(UUID.randomUUID());
        task.setUserId(user.getId());
        task.setLegacyUserId(user.getLegacyId());
        task.setEmail(user.getEmail());
        task.setUsername(user.getUsername());
        task.setInitialName(initialName); task.setProfileVersion(user.getProfileVersion());
        task.setRole(user.getRoles().contains(edu.stankin.cogoalauth.db.entity.enums.Role.ADMIN) ? "ADMIN" : "USER");
        task.setStatus(OutboxStatus.PENDING);
        task.setAttempts(0);
        task.setNextAttemptAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);
    }

    /**
     * Takes up to {@code batch-size} due tasks and pushes their {@code nextAttemptAt} forward by the lease,
     * so neither this nor another instance takes them again while they are being sent. If the instance dies
     * before recording the outcome, the task becomes due again when the lease runs out.
     */
    @Transactional
    public List<ClaimedTask> claimDue() {
        Instant now = clock.instant();
        Instant leaseEnd = now.plus(properties.outbox().lease());
        return taskRepository.lockDue(now, properties.outbox().batchSize()).stream()
                .map(task -> {
                    task.setAttempts(task.getAttempts() + 1);
                    task.setNextAttemptAt(leaseEnd);
                    task.setUpdatedAt(now);
                    return new ClaimedTask(task.getId(), task.getAttempts(),
                            UserProfileEvent.of(task.getId(), task.getCreatedAt(), new UserProfileSnapshot(task.getUserId(), task.getEmail(), task.getUsername(), task.getInitialName(), task.getInitialAvatarUrl(), task.getRole(), task.getProfileVersion(), task.getLegacyUserId())));
                })
                .toList();
    }

    @Transactional
    public void markDone(UUID taskId) {
        update(taskId, task -> {
            task.setStatus(OutboxStatus.DONE);
            task.setInitialName(null);
            task.setInitialAvatarUrl(null);
            task.setLastError(null);
        });
    }

    /** Transient failure: try again after the backoff for this attempt. */
    @Transactional
    public void scheduleRetry(UUID taskId, String error) {
        update(taskId, task -> {
            task.setNextAttemptAt(clock.instant().plus(backoff(task.getAttempts())));
            task.setLastError(truncate(error));
        });
    }

    /** Permanent failure: no more attempts until someone looks into it. */
    @Transactional
    public void markNeedsAttention(UUID taskId, String error) {
        update(taskId, task -> {
            task.setStatus(OutboxStatus.NEEDS_ATTENTION);
            task.setLastError(truncate(error));
        });
    }

    /** {@code initial * 2^(attempt-1)}, capped at {@code max-backoff}: 10s, 20s, 40s, ... 1h, 1h, ... */
    Duration backoff(int attempt) {
        Duration initial = properties.outbox().initialBackoff();
        Duration max = properties.outbox().maxBackoff();
        int exponent = Math.max(0, Math.min(attempt - 1, 30));
        Duration delay = initial.multipliedBy(1L << exponent);
        return delay.compareTo(max) > 0 ? max : delay;
    }

    private void update(UUID taskId, Consumer<MainProfileTask> change) {
        taskRepository.findById(taskId).ifPresent(task -> {
            change.accept(task);
            task.setUpdatedAt(clock.instant());
        });
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }

    /**
     * @param attempt number of this attempt, starting with 1
     */
    public record ClaimedTask(UUID id, int attempt, UserProfileEvent event) {
    }
}
