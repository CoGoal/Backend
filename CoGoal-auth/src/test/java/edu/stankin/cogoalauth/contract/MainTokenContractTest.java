package edu.stankin.cogoalauth.contract;

import edu.stankin.cogoalauth.config.security.JwtConfig;
import edu.stankin.cogoalauth.config.security.JwtProperties;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.service.AccessTokenService;
import edu.stankin.cogoalauth.support.TestData;
import edu.stankin.cogoalmain.config.security.AuthenticatedUser;
import edu.stankin.cogoalmain.config.security.JwtDecoderConfig;
import edu.stankin.cogoalmain.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tokens issued by auth-service are accepted by main's validation code, copied from main unchanged
 * ({@code edu.stankin.cogoalmain.*} in test sources) and configured as main's application.yml does.
 */
class MainTokenContractTest {

    private static final String SECRET = "contract-secret-contract-secret-0123456789";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);

    // main: security.jwt.* with the defaults of its application.yml
    private final JwtTokenService main = mainTokenService(SECRET, "pact-auth", "pact-main");

    private static JwtTokenService mainTokenService(String secret, String issuer, String audience) {
        var properties = new edu.stankin.cogoalmain.config.security.JwtProperties(
                secret, issuer, audience, Duration.ofSeconds(30));
        return new JwtTokenService(new JwtDecoderConfig().jwtDecoder(properties));
    }

    // auth-service: security.jwt.* with the defaults of this service's application.yml
    private static AccessTokenService authTokenService(String secret, Clock clock) {
        JwtProperties properties = new JwtProperties(secret, "pact-auth", "pact-main", ACCESS_TTL, Duration.ofSeconds(30));
        JwtConfig config = new JwtConfig();
        return new AccessTokenService(config.jwtEncoder(properties), config.jwtDecoder(properties), properties, clock);
    }

    @Test
    void mainAcceptsUserToken() {
        User user = TestData.user();

        String token = authTokenService(SECRET, Clock.systemUTC()).issue(user).value();
        AuthenticatedUser parsed = main.parse(token);

        assertThat(parsed.id()).isEqualTo(user.getId());
        assertThat(parsed.roles()).containsExactly("USER");
        assertThat(parsed.issuer()).isEqualTo("pact-auth");
        assertThat(parsed.audience()).containsExactly("pact-main");
        assertThat(Duration.between(parsed.issuedAt(), parsed.expiresAt())).isEqualTo(ACCESS_TTL);
        assertThat(parsed.issuedAt()).isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(1));
    }

    @Test
    void mainSeesAdminRole() {
        User admin = TestData.user(Role.ADMIN);

        AuthenticatedUser parsed = main.parse(authTokenService(SECRET, Clock.systemUTC()).issue(admin).value());

        assertThat(parsed.roles()).containsExactly("USER", "ADMIN");
    }

    @Test
    void mainRejectsTokenSignedWithAnotherSecret() {
        String token = authTokenService("another-secret-another-secret-0123456789", Clock.systemUTC())
                .issue(TestData.user()).value();

        assertThatThrownBy(() -> main.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void mainRejectsExpiredToken() {
        Clock longAgo = Clock.fixed(Instant.now().minus(ACCESS_TTL).minusSeconds(31), ZoneOffset.UTC);
        String token = authTokenService(SECRET, longAgo).issue(TestData.user()).value();

        assertThatThrownBy(() -> main.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void mainToleratesThirtySecondsOfClockSkew() {
        Clock slightlyBehind = Clock.fixed(Instant.now().minus(ACCESS_TTL).plusSeconds(-20), ZoneOffset.UTC);
        String token = authTokenService(SECRET, slightlyBehind).issue(TestData.user()).value();

        assertThat(main.parse(token).id()).isNotNull();
    }

    @Test
    void mainWithOtherAudienceRejectsToken() {
        String token = authTokenService(SECRET, Clock.systemUTC()).issue(TestData.user()).value();

        assertThatThrownBy(() -> mainTokenService(SECRET, "pact-auth", "other-service").parse(token))
                .isInstanceOf(JwtException.class);
    }
}
