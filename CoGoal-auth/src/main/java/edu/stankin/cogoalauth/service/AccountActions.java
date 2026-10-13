package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.ApiException;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import edu.stankin.cogoalauth.web.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import edu.stankin.cogoalauth.db.entity.AccountActionToken;
import edu.stankin.cogoalauth.db.repo.AccountActionTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountActions {
    private final UserRepository users;
    private final PasswordService passwords;
    private final RefreshTokenService refreshTokens;
    private final MainProfileOutbox profiles;
    private final UserMapper mapper;
    private final AccountActionTokenRepository actionTokens;
    private final MailOutboxService mail;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void forgot(String email) {
        users.findByEmail(UserRegistrationService.normalizeEmail(email))
                .ifPresent(user -> issue(user, "PASSWORD_RESET", user.getEmail()));
    }

    @Transactional
    public void reset(String rawToken, String password) {
        AccountActionToken action = claim(rawToken, "PASSWORD_RESET");
        User user = account(action.getUser().getId());
        user.setPasswordHash(passwords.hash(password));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(clock.instant());
        refreshTokens.revokeAll(user.getId());
        consumeAll(user.getId(), "PASSWORD_RESET");
    }

    @Transactional
    public void requestEmail(Long userId, String email, String password) {
        User user = account(userId);
        if (!passwords.matches(password, user.getPasswordHash()))
            throw new ApiException(400, "INVALID_PASSWORD", "Неверный текущий пароль");
        String normalized = UserRegistrationService.normalizeEmail(email);
        if (user.getEmail().equals(normalized))
            throw new ApiException(400, "EMAIL_UNCHANGED", "Укажите новый email");
        if (users.existsByEmail(normalized)) throw emailTaken();
        consumeAll(userId, "EMAIL_CHANGE");
        issue(user, "EMAIL_CHANGE", normalized);
    }

    @Transactional
    public void confirmEmail(String rawToken) {
        AccountActionToken action = claim(rawToken, "EMAIL_CHANGE");
        User user = account(action.getUser().getId());
        if (users.existsByEmail(action.getNewEmail())) throw emailTaken();
        user.setEmail(action.getNewEmail());
        changed(user);
        consumeAll(user.getId(), "EMAIL_CHANGE");
        refreshTokens.revokeAll(user.getId());
    }

    @Transactional
    public UserResponse changeUsername(Long userId, String username) {
        User user = account(userId);
        if (!user.getUsername().equalsIgnoreCase(username) && users.existsByUsernameIgnoreCase(username))
            throw new ApiException(409, "USERNAME_TAKEN", "Этот логин уже занят");
        if (!user.getUsername().equals(username)) {
            user.setUsername(username);
            changed(user);
        }
        return mapper.toResponse(user);
    }

    private User account(Long id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Пользователь не найден"));
    }
    private void changed(User user) {
        user.setProfileVersion(user.getProfileVersion() + 1);
        user.setUpdatedAt(clock.instant());
        profiles.enqueue(user);
    }
    private void issue(User user, String purpose, String email) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = clock.instant().plus(Duration.ofMinutes(30));
        AccountActionToken token = new AccountActionToken();
        token.setUser(user); token.setTokenHash(hash(raw)); token.setType(tokenType(purpose));
        token.setNewEmail(purpose.equals("EMAIL_CHANGE") ? email : null);
        token.setExpiresAt(expires); token.setCreatedAt(clock.instant());
        actionTokens.save(token);
        mail.enqueue(email, purpose, raw, expires);
    }
    private AccountActionToken claim(String raw, String purpose) {
        String hash = hash(raw), type = tokenType(purpose);
        AccountActionToken observed = actionTokens.findByTokenHashAndType(hash, type).orElseThrow(AccountActions::expired);
        if (unusable(observed)) throw expired();
        // Always user before token, including concurrent reset/email-confirm requests.
        account(observed.getUser().getId());
        AccountActionToken locked = actionTokens.lock(hash, type).orElseThrow(AccountActions::expired);
        // Refresh is necessary: the initial read may already be in the persistence context.
        entityManager.refresh(locked, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (unusable(locked)) throw expired();
        return locked;
    }
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private boolean unusable(AccountActionToken token) {
        return token.getUsedAt()!=null || token.getRevokedAt()!=null || !token.getExpiresAt().isAfter(clock.instant());
    }
    private static ApiException expired() { return new ApiException(410,"TOKEN_EXPIRED","Ссылка устарела или уже использована"); }
    private static String tokenType(String purpose) { return purpose.equals("EMAIL_CHANGE") ? "EMAIL_CONFIRM" : purpose; }
    private void consumeAll(Long userId, String purpose) {
        actionTokens.consumeAll(userId, tokenType(purpose), clock.instant());
    }
    private static ApiException emailTaken() { return new ApiException(409, "EMAIL_TAKEN", "Этот email уже используется"); }
    private static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
