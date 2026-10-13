package edu.stankin.cogoalauth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /** Injected wherever "now" matters (token expiry, lockout, outbox), so tests can fix the time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Source of refresh token values. */
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
