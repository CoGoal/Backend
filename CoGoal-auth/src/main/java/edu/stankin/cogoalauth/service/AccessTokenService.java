package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.config.security.AuthenticatedUser;
import edu.stankin.cogoalauth.config.security.JwtProperties;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and validates access tokens: JWS HS256 with the claims main-service expects
 * ({@code iss, sub, aud, iat, exp, roles}).
 */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    public static final String ROLES_CLAIM = "roles";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties properties;
    private final Clock clock;

    /** Roles are taken from the user as they are now; a later role change shows up at the next refresh. */
    public IssuedAccessToken issue(User user) {
        // JWT timestamps are whole seconds
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(ROLES_CLAIM, Role.sortedNames(user.getRoles()))
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new IssuedAccessToken(token, properties.accessTokenTtl().toSeconds());
    }

    /**
     * @throws JwtException if the token is malformed, has a bad signature, is expired
     *                      or its claims do not match the expected ones
     */
    public AuthenticatedUser parse(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        return new AuthenticatedUser(parseUserId(jwt.getSubject()), parseRoles(jwt.getClaim(ROLES_CLAIM)));
    }

    private static Long parseUserId(String subject) {
        try {
            long id = Long.parseLong(subject);
            if (id <= 0) throw new IllegalArgumentException("User id must be positive");
            return id;
        } catch (IllegalArgumentException e) {
            throw new BadJwtException("Claim 'sub' is not a valid user id", e);
        }
    }

    private static Set<String> parseRoles(Object claim) {
        if (!(claim instanceof Collection<?> values)) {
            throw new BadJwtException("Claim 'roles' must be an array of strings");
        }
        Set<String> roles = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String role)) {
                throw new BadJwtException("Claim 'roles' must be an array of strings");
            }
            roles.add(role);
        }
        return roles;
    }

    /**
     * @param expiresIn lifetime in seconds
     */
    public record IssuedAccessToken(String value, long expiresIn) {
    }
}
