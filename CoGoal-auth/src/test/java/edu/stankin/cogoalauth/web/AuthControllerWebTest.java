package edu.stankin.cogoalauth.web;

import edu.stankin.cogoalauth.exception.AuthenticationFailedException;
import edu.stankin.cogoalauth.exception.BusinessRuleException;
import edu.stankin.cogoalauth.exception.InvalidFieldException;
import edu.stankin.cogoalauth.service.AuthService;
import edu.stankin.cogoalauth.support.TestTokens;
import edu.stankin.cogoalauth.support.WebLayerTest;
import edu.stankin.cogoalauth.web.controllers.AuthController;
import edu.stankin.cogoalauth.web.dto.auth.ChangePasswordRequest;
import edu.stankin.cogoalauth.web.dto.auth.RegisterRequest;
import edu.stankin.cogoalauth.web.dto.auth.TokenResponse;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest(AuthController.class)
class AuthControllerWebTest {

    private static final Long USER_ID = edu.stankin.cogoalauth.support.TestData.nextId();
    private static final TokenResponse TOKENS = new TokenResponse("access", "Bearer", 900, "refresh", 2_592_000);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/auth" + path).header(HttpHeaders.AUTHORIZATION, path.equals("/logout") ? TestTokens.bearer(TestTokens.user(USER_ID)) : "").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Nested
    class Register {

        @Test
        void returns201WithTokenPair() throws Exception {
            given(authService.register(any())).willReturn(TOKENS);

            postJson("/register", """
                    {"email":"alice@example.com","username":"alice","name":"alice","password":"password-1"}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").value("access"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.refreshToken").value("refresh"))
                    .andExpect(jsonPath("$.refreshExpiresIn").value(2_592_000));

            verify(authService).register(new RegisterRequest("alice@example.com", "alice", "password-1"));
        }

        @Test
        void validationErrorsListFieldsWithoutEchoingValues() throws Exception {
            postJson("/register", """
                    {"email":"not-an-email","username":"","name":"alice","password":"short"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Проверьте заполнение полей"))
                    .andExpect(jsonPath("$.details[*].field", containsInAnyOrder("email", "username", "password")))
                    .andExpect(content().string(not(containsString("short"))));

            verifyNoInteractions(authService);
        }

        @Test
        void passwordLongerThan72BytesIsRejected() throws Exception {
            // 40 Cyrillic letters = 40 characters but 80 bytes
            String password = "я".repeat(40);

            postJson("/register", """
                    {"email":"alice@example.com","username":"alice","name":"alice","password":"%s"}""".formatted(password))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasSize(1)))
                    .andExpect(jsonPath("$.details[0].field").value("password"))
                    .andExpect(jsonPath("$.details[0].message").value("Некорректное значение"));
        }

        @Test
        void usernameLongerThanMainAllowsIsRejected() throws Exception {
            postJson("/register", """
                    {"email":"alice@example.com","username":"%s","name":"alice","password":"password-1"}""".formatted("a".repeat(51)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("username"));
        }

        @Test
        void takenEmailIs409() throws Exception {
            given(authService.register(any())).willThrow(new edu.stankin.cogoalauth.exception.ApiException(409,"EMAIL_TAKEN","Этот email уже используется"));

            postJson("/register", """
                    {"email":"alice@example.com","username":"alice","name":"alice","password":"password-1"}""")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Этот email уже используется"));
        }

        @Test
        void malformedJsonIs400() throws Exception {
            postJson("/register", "{").andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Login {

        @Test
        void returnsTokenPair() throws Exception {
            given(authService.login(any())).willReturn(TOKENS);

            postJson("/login", """
                    {"login":"alice@example.com","password":"password-1"}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access"));
        }

        @Test
        void wrongCredentialsAre401ProblemDetail() throws Exception {
            given(authService.login(any()))
                    .willThrow(new AuthenticationFailedException(AuthenticationFailedException.INVALID_CREDENTIALS));

            postJson("/login", """
                    {"login":"alice@example.com","password":"wrong-password"}""")
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Неверный email, логин или пароль"));
        }
    }

    @Nested
    class RefreshAndLogout {

        @Test
        void refreshNeedsNoAccessToken() throws Exception {
            given(authService.refresh("refresh-value")).willReturn(TOKENS);

            postJson("/refresh", """
                    {"refreshToken":"refresh-value"}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.refreshToken").value("refresh"));
        }

        @Test
        void invalidRefreshTokenIs401() throws Exception {
            given(authService.refresh(any()))
                    .willThrow(new AuthenticationFailedException(AuthenticationFailedException.INVALID_REFRESH_TOKEN));

            postJson("/refresh", """
                    {"refreshToken":"used"}""")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Refresh-токен недействителен или истёк"));
        }

        @Test
        void logoutIs204() throws Exception {
            postJson("/logout", """
                    {"refreshToken":"refresh-value"}""")
                    .andExpect(status().isNoContent());

            verify(authService).logout("refresh-value");
        }

        @Test
        void logoutAllNeedsAccessToken() throws Exception {
            mvc.perform(post("/api/v1/auth/logout-all"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Требуется авторизация"));

            mvc.perform(post("/api/v1/auth/logout-all")
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(USER_ID))))
                    .andExpect(status().isNoContent());
            verify(authService).logoutAll(USER_ID);
        }
    }

    @Nested
    class Me {

        @Test
        void returnsCurrentUser() throws Exception {
            given(authService.me(USER_ID)).willReturn(new UserResponse(USER_ID, "alice@example.com", "alice",
                    List.of("USER"), Instant.parse("2026-09-28T10:00:00Z")));

            mvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(USER_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(USER_ID.intValue()))
                    .andExpect(jsonPath("$.email").value("alice@example.com"))
                    .andExpect(jsonPath("$.roles[0]").value("USER"))
                    .andExpect(jsonPath("$.createdAt").value("2026-09-28T10:00:00Z"));
        }

        @Test
        void invalidTokenIs401WithWwwAuthenticate() throws Exception {
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                    .andExpect(jsonPath("$.message").value("Требуется авторизация"));
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void returns204() throws Exception {
            mvc.perform(post("/api/v1/auth/change-password")
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"password-1","newPassword":"password-2"}"""))
                    .andExpect(status().isNoContent());

            verify(authService).changePassword(USER_ID, new ChangePasswordRequest("password-1", "password-2"));
        }

        @Test
        void wrongCurrentPasswordIsFieldError() throws Exception {
            willThrow(new InvalidFieldException("currentPassword", "is incorrect"))
                    .given(authService).changePassword(eq(USER_ID), any());

            mvc.perform(post("/api/v1/auth/change-password")
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.user(USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"wrong-password","newPassword":"password-2"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("currentPassword"))
                    .andExpect(jsonPath("$.details[0].message").value("Некорректное значение"));
        }

        @Test
        void requiresAccessToken() throws Exception {
            postJson("/change-password", """
                    {"currentPassword":"password-1","newPassword":"password-2"}""")
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(authService);
        }
    }
}
