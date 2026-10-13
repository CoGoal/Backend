package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.AuthenticationFailedException;
import edu.stankin.cogoalauth.exception.InvalidFieldException;
import edu.stankin.cogoalauth.service.AccessTokenService.IssuedAccessToken;
import edu.stankin.cogoalauth.service.RefreshTokenService.IssuedRefreshToken;
import edu.stankin.cogoalauth.service.RefreshTokenService.Rotation;
import edu.stankin.cogoalauth.support.TestData;
import edu.stankin.cogoalauth.web.dto.auth.ChangePasswordRequest;
import edu.stankin.cogoalauth.web.dto.auth.LoginRequest;
import edu.stankin.cogoalauth.web.dto.auth.RegisterRequest;
import edu.stankin.cogoalauth.web.dto.auth.TokenResponse;
import edu.stankin.cogoalauth.web.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct-horse";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRegistrationService userRegistrationService;
    @Mock
    private PasswordService passwordService;
    @Mock
    private AccessTokenService accessTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserMapper userMapper;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, userRegistrationService, passwordService, accessTokenService,
                refreshTokenService, userMapper, TestData.properties(), Clock.fixed(TestData.NOW, ZoneOffset.UTC));
        user = TestData.user();
    }

    private void tokensAreIssued() {
        given(accessTokenService.issue(any())).willReturn(new IssuedAccessToken("access", 900));
        given(refreshTokenService.startFamily(any())).willReturn(new IssuedRefreshToken("refresh", 2_592_000));
    }

    @Nested
    class Register {

        @Test
        void createsUserWithRoleUserAndReturnsTokens() {
            given(userRegistrationService.create("Alice@Example.com", "alice", "alice", PASSWORD, Set.of(Role.USER)))
                    .willReturn(user);
            tokensAreIssued();

            TokenResponse response = authService.register(new RegisterRequest("Alice@Example.com", "alice", PASSWORD));

            assertThat(response).isEqualTo(new TokenResponse("access", "Bearer", 900, "refresh", 2_592_000));
            verify(refreshTokenService).startFamily(user);
        }
    }

    @Nested
    class Login {

        @Test
        void succeedsAndResetsFailedAttempts() {
            user.setFailedLoginAttempts(3);
            given(userRepository.findByEmailForUpdate("alice@example.com")).willReturn(Optional.of(user));
            given(passwordService.matches(PASSWORD, user.getPasswordHash())).willReturn(true);
            tokensAreIssued();

            TokenResponse response = authService.login(new LoginRequest("  ALICE@example.com ", PASSWORD));

            assertThat(response.accessToken()).isEqualTo("access");
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
        }

        @Test
        void unknownEmailGetsTheSameAnswerAsWrongPassword() {
            given(userRepository.findByEmailForUpdate("nobody@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD)))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessage("Неверный email, логин или пароль");
            // A hash is still checked, so the response time does not reveal that the email is unknown
            verify(passwordService).matches(eq(PASSWORD), isNull());
        }

        @Test
        void wrongPasswordIncrementsCounter() {
            user.setFailedLoginAttempts(1);
            given(userRepository.findByEmailForUpdate("alice@example.com")).willReturn(Optional.of(user));
            given(passwordService.matches("wrong-password", user.getPasswordHash())).willReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-password")))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessage("Неверный email, логин или пароль");

            assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
            assertThat(user.getLockedUntil()).isNull();
            verify(refreshTokenService, never()).startFamily(any());
        }

        @Test
        void fifthWrongPasswordLocksTheAccount() {
            user.setFailedLoginAttempts(4);
            given(userRepository.findByEmailForUpdate("alice@example.com")).willReturn(Optional.of(user));
            given(passwordService.matches(anyString(), eq(user.getPasswordHash()))).willReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-password")))
                    .isInstanceOf(AuthenticationFailedException.class);

            assertThat(user.getLockedUntil()).isEqualTo(TestData.NOW.plus(Duration.ofMinutes(15)));
            assertThat(user.getFailedLoginAttempts()).isZero();
        }

        @Test
        void lockedAccountRejectsEvenTheCorrectPassword() {
            user.setLockedUntil(TestData.NOW.plusSeconds(60));
            given(userRepository.findByEmailForUpdate("alice@example.com")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", PASSWORD)))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("Слишком много попыток входа");

            verify(passwordService, never()).matches(PASSWORD, user.getPasswordHash());
            verify(refreshTokenService, never()).startFamily(any());
        }

        @Test
        void lockEndsAfterItsDuration() {
            user.setLockedUntil(TestData.NOW);
            given(userRepository.findByEmailForUpdate("alice@example.com")).willReturn(Optional.of(user));
            given(passwordService.matches(PASSWORD, user.getPasswordHash())).willReturn(true);
            tokensAreIssued();

            authService.login(new LoginRequest("alice@example.com", PASSWORD));

            assertThat(user.getLockedUntil()).isNull();
        }
    }

    @Nested
    class Refresh {

        @Test
        void issuesAccessTokenForTheTokenOwner() {
            given(refreshTokenService.rotate("old")).willReturn(new Rotation(user, new IssuedRefreshToken("new", 100)));
            given(accessTokenService.issue(user)).willReturn(new IssuedAccessToken("access", 900));

            TokenResponse response = authService.refresh("old");

            assertThat(response.refreshToken()).isEqualTo("new");
            assertThat(response.accessToken()).isEqualTo("access");
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void changesHashAndRevokesAllRefreshTokens() {
            given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
            given(passwordService.matches(PASSWORD, user.getPasswordHash())).willReturn(true);
            given(passwordService.hash("new-password-1")).willReturn("new-hash");

            authService.changePassword(user.getId(), new ChangePasswordRequest(PASSWORD, "new-password-1"));

            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
            verify(refreshTokenService).revokeAll(user.getId());
        }

        @Test
        void wrongCurrentPasswordIsAFieldError() {
            given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
            given(passwordService.matches("wrong-password", user.getPasswordHash())).willReturn(false);

            assertThatThrownBy(() -> authService.changePassword(user.getId(),
                    new ChangePasswordRequest("wrong-password", "new-password-1")))
                    .isInstanceOfSatisfying(InvalidFieldException.class,
                            e -> assertThat(e.getField()).isEqualTo("currentPassword"));

            verify(refreshTokenService, never()).revokeAll(any());
        }

        @Test
        void newPasswordMustDiffer() {
            given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
            given(passwordService.matches(PASSWORD, user.getPasswordHash())).willReturn(true);

            assertThatThrownBy(() -> authService.changePassword(user.getId(),
                    new ChangePasswordRequest(PASSWORD, PASSWORD)))
                    .isInstanceOfSatisfying(InvalidFieldException.class,
                            e -> assertThat(e.getField()).isEqualTo("newPassword"));
        }
    }
}
