package edu.stankin.cogoalauth.service.scheduler;

import edu.stankin.cogoalauth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired refresh tokens so the table does not grow forever.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(fixedDelayString = "${app.refresh-token.cleanup-interval}",
            initialDelayString = "${app.refresh-token.cleanup-interval}")
    public void deleteExpired() {
        try {
            int deleted = refreshTokenService.deleteExpired();
            if (deleted > 0) {
                log.info("Deleted {} expired refresh token(s)", deleted);
            }
        } catch (RuntimeException e) {
            log.error("Refresh token cleanup failed", e);
        }
    }
}
