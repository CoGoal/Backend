package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.BusinessRuleException;
import edu.stankin.cogoalauth.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordService passwordService;
    @Mock
    private MainProfileOutbox outbox;

    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new UserRegistrationService(userRepository, passwordService, outbox,
                Clock.fixed(TestData.NOW, ZoneOffset.UTC));
    }

    @Test
    void createsUserWithNormalizedEmailHashedPasswordAndOutboxTask() {
        given(passwordService.hash("password-1")).willReturn("hash");
        given(userRepository.save(any())).willAnswer(invocation -> { User saved=invocation.getArgument(0); saved.setId(TestData.nextId()); return saved; });

        User user = service.create("  Alice@Example.COM ", " Alice ", "password-1", Set.of());

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getUsername()).isEqualTo("Alice");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getRoles()).containsExactly(Role.USER);
        assertThat(user.getCreatedAt()).isEqualTo(TestData.NOW);
        verify(outbox).enqueueRegistration(user, "Alice");
    }

    @Test
    void keepsUserRoleForAdmins() {
        given(userRepository.save(any())).willAnswer(invocation -> { User saved=invocation.getArgument(0); saved.setId(TestData.nextId()); return saved; });

        User user = service.create("root@example.com", "root", "password-1", Set.of(Role.ADMIN));

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
    }

    @Test
    void takenEmailIs409() {
        given(userRepository.existsByEmail("alice@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.create("ALICE@example.com", "alice2", "password-1", Set.of()))
                .isInstanceOf(edu.stankin.cogoalauth.exception.ApiException.class)
                .hasMessage("Этот email уже используется");
        verify(userRepository, never()).save(any());
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void usernameIsUniqueIgnoringCase() {
        given(userRepository.existsByUsernameIgnoreCase("ALICE")).willReturn(true);

        assertThatThrownBy(() -> service.create("other@example.com", "ALICE", "password-1", Set.of()))
                .isInstanceOf(edu.stankin.cogoalauth.exception.ApiException.class)
                .hasMessage("Этот логин уже занят");
    }
}
