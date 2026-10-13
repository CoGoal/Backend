package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.config.AppProperties;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.BusinessRuleException;
import edu.stankin.cogoalauth.web.dto.auth.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the first administrator from {@code BOOTSTRAP_ADMIN_EMAIL} / {@code BOOTSTRAP_ADMIN_PASSWORD}
 * (and optional {@code BOOTSTRAP_ADMIN_USERNAME}) when no user with that email exists yet.
 * An existing user is left as is. Invalid settings stop the application with a message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final UserRegistrationService userRegistrationService;
    private final TransactionTemplate transactionTemplate;
    private final Validator validator;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (!admin.configured()) {
            return;
        }
        RegisterRequest request = new RegisterRequest(admin.email(), admin.username(), admin.password());
        validate(request);

        try {
            transactionTemplate.executeWithoutResult(status -> createIfAbsent(request));
        } catch (DataIntegrityViolationException e) {
            // Another instance created it at the same moment
            log.info("Bootstrap admin was created concurrently by another instance");
        } catch (BusinessRuleException | edu.stankin.cogoalauth.exception.ApiException e) {
            throw new IllegalStateException("Cannot create the bootstrap admin: " + e.getMessage()
                    + " (set another BOOTSTRAP_ADMIN_USERNAME)", e);
        }
    }

    private void createIfAbsent(RegisterRequest request) {
        if (userRepository.existsByEmail(UserRegistrationService.normalizeEmail(request.email()))) {
            log.info("Bootstrap admin already exists, nothing to do");
            return;
        }
        User user = userRegistrationService.create(request.email(), request.username(), request.name(), request.password(),
                EnumSet.of(Role.USER, Role.ADMIN));
        log.info("Created bootstrap admin {} ({})", user.getId(), user.getEmail());
    }

    // The same rules as /register; values are not included in the message, only the fields
    private void validate(RegisterRequest request) {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String problems = violations.stream()
                    .map(v -> "BOOTSTRAP_ADMIN_" + v.getPropertyPath().toString().toUpperCase() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalStateException("Invalid bootstrap admin settings: " + problems);
        }
    }
}
