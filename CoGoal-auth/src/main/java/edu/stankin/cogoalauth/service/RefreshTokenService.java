package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.config.AppProperties;
import edu.stankin.cogoalauth.db.entity.RefreshToken;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.repo.RefreshTokenRepository;
import edu.stankin.cogoalauth.exception.AuthenticationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens: 256 random bits, base64url. Only the SHA-256 of the value is stored.
 * <p>
 * Every refresh revokes the presented token and issues a new one in the same family (rotation).
 * A revoked token presented again means it was copied: the whole family is revoked, so both the thief
 * and the owner have to log in again.
 * <p>
 * Methods run in the caller's transaction ({@link AuthService}), which must not roll back on
 * {@link AuthenticationFailedException} — otherwise the family revocation would be lost. The same
 * {@code noRollbackFor} is needed here too: a participating method that lets the exception through
 * would otherwise mark the whole transaction rollback-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = AuthenticationFailedException.class)
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    /** First token of a new login. */
    public IssuedRefreshToken startFamily(User user) {
        return issue(user, UUID.randomUUID());
    }

    /**
     * @throws AuthenticationFailedException unknown, expired or already used token
     */
    public Rotation rotate(String value) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hash(value))
                .orElseThrow(RefreshTokenService::invalidToken);

        if (token.isRevoked()) {
            int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            log.warn("Revoked refresh token reused (user {}, family {}): revoked {} active token(s) of the family",
                    token.getUser().getId(), token.getFamilyId(), revoked);
            throw invalidToken();
        }
        if (token.isExpiredAt(now)) {
            throw invalidToken();
        }

        token.setRevokedAt(now);
        User user = token.getUser();
        return new Rotation(user, issue(user, token.getFamilyId()));
    }

    /** Logout of one session. Unknown and already revoked tokens are ignored, so logout is idempotent. */
    public void revoke(String value) {
        refreshTokenRepository.findByTokenHashForUpdate(hash(value))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(clock.instant()));
    }

    /** Logout everywhere: all active tokens of the user. */
    public int revokeAll(Long userId) {
        return refreshTokenRepository.revokeAllOfUser(userId, clock.instant());
    }

    /** Housekeeping: an expired token is rejected anyway, its row is no longer needed. */
    @Transactional
    public int deleteExpired() {
        return refreshTokenRepository.deleteExpiredBefore(clock.instant());
    }

    private IssuedRefreshToken issue(User user, UUID familyId) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        Instant now = clock.instant();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId(familyId);
        token.setTokenHash(hash(value));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(properties.refreshToken().ttl()));
        refreshTokenRepository.save(token);

        return new IssuedRefreshToken(value, properties.refreshToken().ttl().toSeconds());
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static AuthenticationFailedException invalidToken() {
        return new AuthenticationFailedException(AuthenticationFailedException.INVALID_REFRESH_TOKEN);
    }

    /**
     * @param expiresIn lifetime in seconds
     */
    public record IssuedRefreshToken(String value, long expiresIn) {
    }

    /**
     * @param user owner of the token, with current roles for the new access token
     */
    public record Rotation(User user, IssuedRefreshToken refreshToken) {
    }
}
