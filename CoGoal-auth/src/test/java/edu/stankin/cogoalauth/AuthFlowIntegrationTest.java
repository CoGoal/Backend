package edu.stankin.cogoalauth;

import edu.stankin.cogoalauth.db.entity.MainProfileTask;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.OutboxStatus;
import edu.stankin.cogoalauth.db.repo.MainProfileTaskRepository;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.AuthenticationFailedException;
import edu.stankin.cogoalauth.exception.BusinessRuleException;
import edu.stankin.cogoalauth.service.AccessTokenService;
import edu.stankin.cogoalauth.service.AuthService;
import edu.stankin.cogoalauth.kafka.producer.ProfileOutboxPublisher;
import edu.stankin.cogoalauth.kafka.producer.UserProfilePublisher;
import edu.stankin.cogoalauth.kafka.event.UserProfileEvent;
import edu.stankin.cogoalauth.kafka.event.UserProfileSnapshot;
import edu.stankin.cogoalauth.support.PostgresIntegrationTest;
import edu.stankin.cogoalauth.web.dto.auth.ChangePasswordRequest;
import edu.stankin.cogoalauth.web.dto.auth.LoginRequest;
import edu.stankin.cogoalauth.web.dto.auth.RegisterRequest;
import edu.stankin.cogoalauth.web.dto.auth.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Registration, login, refresh and the outbox on a real PostgreSQL, through the transactional services.
 * main is replaced by a mock; the outbox is run by hand.
 */
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "password-1";

    @Autowired
    private AuthService authService;
    @Autowired
    private AccessTokenService accessTokenService;
    @Autowired
    private ProfileOutboxPublisher outboxProcessor;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MainProfileTaskRepository taskRepository;

    @MockitoBean
    private UserProfilePublisher publisher;

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Registered(Long id, String email, TokenResponse tokens) {
    }

    private Registered register() {
        String name = unique("user");
        String email = name + "@Example.com";
        TokenResponse tokens = authService.register(new RegisterRequest(email, name, PASSWORD));
        Long id = accessTokenService.parse(tokens.accessToken()).id();
        return new Registered(id, email.toLowerCase(), tokens);
    }

    private MainProfileTask taskOf(Long userId) {
        List<MainProfileTask> tasks = taskRepository.findByUserId(userId);
        assertThat(tasks).hasSize(1);
        return tasks.get(0);
    }

    @Test
    void registrationWritesOutboxTaskThatIsSentToMain() {
        Registered alice = register();

        assertThat(taskOf(alice.id()).getStatus()).isEqualTo(OutboxStatus.PENDING);
        User stored = userRepository.findById(alice.id()).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo(alice.email());

        outboxProcessor.processDue();

        var captured=org.mockito.ArgumentCaptor.forClass(UserProfileEvent.class);
        org.mockito.Mockito.verify(publisher,org.mockito.Mockito.atLeastOnce()).publish(captured.capture());
        var delivered=captured.getAllValues().stream().filter(event->event.user().id().equals(alice.id())).toList();
        assertThat(delivered).singleElement().satisfies(event->{
            assertThat(event.eventId()).isEqualTo(taskOf(alice.id()).getId());
            assertThat(event.eventType()).isEqualTo("UserRegistered");
            assertThat(event.user()).isEqualTo(new UserProfileSnapshot(alice.id(),alice.email(),stored.getUsername()));
        });
        assertThat(taskOf(alice.id()).getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    void brokerBeingDownDoesNotBreakRegistrationAndTheTaskIsRetriedLater() {
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unreachable")).when(publisher).publish(any());
        Registered bob = register();

        outboxProcessor.processDue();

        MainProfileTask task = taskOf(bob.id());
        assertThat(task.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(task.getLastError()).contains("Kafka publication failed");
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        Registered carol = register();

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest(carol.email().toUpperCase(), unique("other"), PASSWORD)))
                .isInstanceOf(edu.stankin.cogoalauth.exception.ApiException.class);
    }

    @Test
    void failedLoginsAreSavedAndLockTheAccount() {
        Registered dave = register();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(dave.email(), "wrong-password")))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        assertThat(userRepository.findById(dave.id()).orElseThrow().getLockedUntil()).isAfter(Instant.now());
        assertThatThrownBy(() -> authService.login(new LoginRequest(dave.email(), PASSWORD)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("Слишком много попыток входа");
    }

    @Test
    void refreshRotatesAndReuseRevokesTheWholeFamily() {
        Registered erin = register();
        String first = erin.tokens().refreshToken();

        TokenResponse second = authService.refresh(first);
        assertThat(second.refreshToken()).isNotEqualTo(first);

        // The first token is replayed: rejected, and the family (including the second token) is revoked
        assertThatThrownBy(() -> authService.refresh(first)).isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> authService.refresh(second.refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);

        // Other logins are not affected
        TokenResponse otherSession = authService.login(new LoginRequest(erin.email(), PASSWORD));
        assertThat(authService.refresh(otherSession.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void logoutIsIdempotent() {
        Registered frank = register();

        authService.logout(frank.tokens().refreshToken());
        authService.logout(frank.tokens().refreshToken());

        assertThatThrownBy(() -> authService.refresh(frank.tokens().refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void changePasswordRevokesAllSessions() {
        Registered grace = register();
        TokenResponse secondSession = authService.login(new LoginRequest(grace.email(), PASSWORD));

        authService.changePassword(grace.id(), new ChangePasswordRequest(PASSWORD, "new-password-2"));

        assertThatThrownBy(() -> authService.refresh(grace.tokens().refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> authService.refresh(secondSession.refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> authService.login(new LoginRequest(grace.email(), PASSWORD)))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThat(authService.login(new LoginRequest(grace.email(), "new-password-2")).accessToken()).isNotBlank();
    }
}
