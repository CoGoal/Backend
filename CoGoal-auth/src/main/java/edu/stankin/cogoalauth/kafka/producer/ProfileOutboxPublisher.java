package edu.stankin.cogoalauth.kafka.producer;
import edu.stankin.cogoalauth.service.MainProfileOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Transactional outbox: broker failures never roll back registration or lose the pending event. */
@Component @RequiredArgsConstructor @Slf4j
public class ProfileOutboxPublisher {
    private final MainProfileOutbox outbox;
    private final UserProfilePublisher publisher;
    @Scheduled(fixedDelayString="${app.outbox.poll-interval}")
    public void run() {
        try { processDue(); }
        catch (RuntimeException exception) { log.error("Profile outbox polling failed",exception); }
    }
    public int processDue() {
        var tasks=outbox.claimDue();
        for (var task:tasks) {
            try { publisher.publish(task.event());outbox.markDone(task.id()); }
            catch (RuntimeException exception) {
                outbox.scheduleRetry(task.id(),"Kafka publication failed: "+exception.getClass().getSimpleName());
                log.warn("Profile event for user {} remains pending",task.event().user().id());
            }
        }
        return tasks.size();
    }
}
