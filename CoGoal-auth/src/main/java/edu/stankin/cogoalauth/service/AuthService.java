package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.config.AppProperties;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.AuthenticationFailedException;
import edu.stankin.cogoalauth.exception.InvalidFieldException;
import edu.stankin.cogoalauth.exception.NotFoundException;
import edu.stankin.cogoalauth.service.AccessTokenService.IssuedAccessToken;
import edu.stankin.cogoalauth.service.RefreshTokenService.IssuedRefreshToken;
import edu.stankin.cogoalauth.service.RefreshTokenService.Rotation;
import edu.stankin.cogoalauth.web.dto.auth.ChangePasswordRequest;
import edu.stankin.cogoalauth.web.dto.auth.LoginRequest;
import edu.stankin.cogoalauth.web.dto.auth.RegisterRequest;
import edu.stankin.cogoalauth.web.dto.auth.TokenResponse;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import edu.stankin.cogoalauth.web.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.stankin.cogoalauth.exception.AccountLockedException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registration, login, token refresh, logout and password change.
 * <p>
 * Login and refresh do not roll back on {@link AuthenticationFailedException}: the failed attempt counter,
 * the lock and the revoked token family must be saved although the request is rejected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserRegistrationService userRegistrationService;
    private final PasswordService passwordService;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final AppProperties properties;
    private final Clock clock;

    /**
     * New account with role USER, plus an outbox task to create the profile in main.
     * The tokens work right away, even before main has the profile.
     */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        User user = userRegistrationService.create(
                request.email(), request.username(), request.name(), request.password(), Set.of(Role.USER));
        log.info("Registered user {}", user.getId());
        return tokens(user, refreshTokenService.startFamily(user));
    }

    /**
     * Unknown identifiers and wrong passwords give the same 401; locked accounts give 423.
     * Email and username share a user-row lock and a single failed-attempt counter.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public TokenResponse login(LoginRequest request) {
        Instant now = clock.instant();
        String login = request.login().trim();
        Optional<User> found = login.contains("@")
                ? userRepository.findByEmailForUpdate(UserRegistrationService.normalizeEmail(login))
                : userRepository.findByUsernameForUpdate(login);

        if (found.isEmpty()) {
            passwordService.matches(request.password(), null);
            throw invalidCredentials();
        }
        User user = found.get();
        if (user.isLockedAt(now)) {
            passwordService.matches(request.password(), null);
            log.info("Login of locked user {} rejected", user.getId());
            throw new AccountLockedException(user.getLockedUntil());
        }
        if (!passwordService.matches(request.password(), user.getPasswordHash())) {
            recordFailedLogin(user, now);
            throw invalidCredentials();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return tokens(user, refreshTokenService.startFamily(user));
    }

    /** New pair; the presented refresh token is used up. Roles in the new access token are the current ones. */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public TokenResponse refresh(String refreshToken) {
        Rotation rotation = refreshTokenService.rotate(refreshToken);
        return tokens(rotation.user(), rotation.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional
    public void logoutAll(Long userId) {
        int revoked = refreshTokenService.revokeAll(userId);
        log.info("User {} logged out everywhere, {} refresh token(s) revoked", userId, revoked);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userMapper.toResponse(findUser(userId));
    }

    /** Also revokes all refresh tokens: every session has to log in again with the new password. */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordService.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidFieldException("currentPassword", "is incorrect");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new InvalidFieldException("newPassword", "must differ from the current password");
        }
        user.setPasswordHash(passwordService.hash(request.newPassword()));
        user.setUpdatedAt(clock.instant());
        int revoked = refreshTokenService.revokeAll(userId);
        log.info("User {} changed the password, {} refresh token(s) revoked", userId, revoked);
    }

    private void recordFailedLogin(User user, Instant now) {
        AppProperties.Login settings = properties.login();
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= settings.maxFailedAttempts()) {
            // The counter starts again after the lock, so the next lock needs another full series
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(now.plus(settings.lockDuration()));
            log.warn("User {} locked until {} after {} failed logins in a row", user.getId(), user.getLockedUntil(), attempts);
        } else {
            user.setFailedLoginAttempts(attempts);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }

    private TokenResponse tokens(User user, IssuedRefreshToken refreshToken) {
        IssuedAccessToken accessToken = accessTokenService.issue(user);
        return new TokenResponse(accessToken.value(), TokenResponse.BEARER, accessToken.expiresIn(),
                refreshToken.value(), refreshToken.expiresIn(), userMapper.toResponse(user));
    }

    private static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException(AuthenticationFailedException.INVALID_CREDENTIALS);
    }
}
