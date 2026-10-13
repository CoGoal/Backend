package edu.stankin.cogoalauth.web.controllers;

import edu.stankin.cogoalauth.config.OpenApiConfig;
import edu.stankin.cogoalauth.config.security.AuthenticatedUser;
import edu.stankin.cogoalauth.config.security.SecurityConfig;
import edu.stankin.cogoalauth.service.AuthService;
import edu.stankin.cogoalauth.web.dto.auth.ChangePasswordRequest;
import edu.stankin.cogoalauth.web.dto.auth.LoginRequest;
import edu.stankin.cogoalauth.web.dto.auth.RefreshTokenRequest;
import edu.stankin.cogoalauth.web.dto.auth.RegisterRequest;
import edu.stankin.cogoalauth.web.dto.auth.TokenResponse;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityConfig.API_PREFIX)
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login and tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register with role USER and get a token pair",
            description = "The profile in main is created asynchronously; until then main answers 404 for it. "
                    + "409 if the email or username (ignoring case) is taken.")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Вход по email или логину и паролю",
            description = "401 при неверных данных; 423 при блокировке аккаунта после пяти попыток на 30 минут")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new pair",
            description = "The presented token is used up. Presenting a used token again revokes all tokens of that login.")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token", description = "Idempotent: an unknown or revoked token is also 204")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Revoke all refresh tokens of the current user",
            description = "Access tokens already issued stay valid until they expire")
    public void logoutAll(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.logoutAll(user.id());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Current user")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.id());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Change the password and revoke all refresh tokens",
            description = "400 with field currentPassword if it is wrong")
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.id(), request);
    }
}
