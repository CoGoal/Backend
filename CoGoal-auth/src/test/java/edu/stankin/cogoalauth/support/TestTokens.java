package edu.stankin.cogoalauth.support;

import edu.stankin.cogoalauth.service.AccessTokenService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Secrets for test contexts and hand-made access tokens for web tests.
 */
public final class TestTokens {

    public static final String SECRET = "test-secret-test-secret-test-secret-123";
    public static final String ISSUER = "pact-auth";
    public static final String AUDIENCE = "pact-main";
    public static final String INTERNAL_TOKEN = "test-internal-token-123456";

    /** Properties that make a Spring test context start: the two required secrets, scheduling off. */
    public static final String[] PROPERTIES = {
            "security.jwt.secret=" + SECRET,
            "app.internal.token=" + INTERNAL_TOKEN,
            "app.scheduling.enabled=false"
    };

    private TestTokens() {
    }

    public static String user(Long userId) {
        return token(userId, List.of("USER"));
    }

    public static String admin(Long userId) {
        return token(userId, List.of("USER", "ADMIN"));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static String token(Long userId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim(AccessTokenService.ROLES_CLAIM, roles)
                .build();

        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
