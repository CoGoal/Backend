package edu.stankin.cogoalauth.config;

import edu.stankin.cogoalauth.config.security.JwtProperties;
import edu.stankin.cogoalauth.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import edu.stankin.cogoalauth.kafka.config.ProfileKafkaProperties;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml binds to the settings records with the documented defaults, and the application
 * refuses to start without the secrets or with too short ones.
 */
class StartupConfigurationTest {

    @Configuration
    @EnableConfigurationProperties({JwtProperties.class, AppProperties.class, ProfileKafkaProperties.class})
    static class Settings {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            })
            .withUserConfiguration(Settings.class);

    @Test
    void bindsDefaults() {
        runner.withPropertyValues("JWT_SECRET=" + TestTokens.SECRET, "INTERNAL_TOKEN=" + TestTokens.INTERNAL_TOKEN)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtProperties jwt = context.getBean(JwtProperties.class);
                    assertThat(jwt.issuer()).isEqualTo("pact-auth");
                    assertThat(jwt.audience()).isEqualTo("pact-main");
                    assertThat(jwt.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(jwt.clockSkew()).isEqualTo(Duration.ofSeconds(30));

                    AppProperties app = context.getBean(AppProperties.class);
                    assertThat(app.refreshToken().ttl()).isEqualTo(Duration.ofDays(30));
                    assertThat(app.login().maxFailedAttempts()).isEqualTo(5);
                    assertThat(app.login().lockDuration()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(context.getBean(ProfileKafkaProperties.class).profileTopic()).isEqualTo("cogoal.user-profiles.v1");
                    assertThat(app.cors().allowedOrigins()).isEmpty();
                    assertThat(app.bootstrapAdmin().configured()).isFalse();
                });
    }

    @Test
    void failsWithoutJwtSecret() {
        runner.withPropertyValues("INTERNAL_TOKEN=" + TestTokens.INTERNAL_TOKEN)
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContainingAll("security.jwt", "secret", "must not be blank"));
    }

    @Test
    void failsWithShortJwtSecret() {
        runner.withPropertyValues("JWT_SECRET=too-short", "INTERNAL_TOKEN=" + TestTokens.INTERNAL_TOKEN)
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("at least 32 characters"));
    }

    @Test
    void noHttpInternalTokenIsRequired() {
        runner.withPropertyValues("JWT_SECRET="+TestTokens.SECRET).run(context->assertThat(context).hasNotFailed());
    }
    @Test
    void invalidKafkaPartitionCountFails() {
        runner.withPropertyValues("JWT_SECRET="+TestTokens.SECRET,"app.kafka.partitions=0")
            .run(context->assertThat(context).hasFailed());
    }
}
