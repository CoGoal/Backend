package edu.stankin.cogoalauth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduled jobs: sending the main-service outbox and deleting expired refresh tokens.
 * Set {@code app.scheduling.enabled=false} to switch them off, e.g. in tests.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
