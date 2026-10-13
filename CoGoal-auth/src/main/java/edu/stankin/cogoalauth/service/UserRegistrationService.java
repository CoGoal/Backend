package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import edu.stankin.cogoalauth.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Creates accounts: for {@code /register} and for the bootstrap admin.
 * The outbox task for main is written in the same transaction as the user.
 */
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final MainProfileOutbox mainProfileOutbox;
    private final Clock clock;

    /**
     * @param roles {@link Role#USER} is added in any case
     * @throws BusinessRuleException email or username (ignoring case) is already taken
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User create(String email, String username, String rawPassword, Set<Role> roles) {
        return create(email, username, username, rawPassword, roles);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public User create(String email, String username, String name, String rawPassword, Set<Role> roles) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedUsername = username.trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(409, "EMAIL_TAKEN", "Этот email уже используется");
        }
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ApiException(409, "USERNAME_TAKEN", "Этот логин уже занят");
        }

        Instant now = clock.instant();
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setUsername(normalizedUsername);
        user.setProfileVersion(1);
        user.setPasswordHash(passwordService.hash(rawPassword));
        EnumSet<Role> allRoles = EnumSet.of(Role.USER);
        allRoles.addAll(roles);
        user.setRoles(allRoles);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        // A concurrent registration with the same email/username fails here or at commit on the unique index → 409
        User saved = userRepository.save(user);

        mainProfileOutbox.enqueueRegistration(saved, name.trim());
        return saved;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
