package edu.stankin.cogoalauth.web.controllers;
import edu.stankin.cogoalauth.config.security.AuthenticatedUser;
import edu.stankin.cogoalauth.service.AccountActions;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import edu.stankin.cogoalauth.web.dto.validation.MaxBytes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1") @Tag(name="Auth")
public class AccountActionsController {
    private final AccountActions actions;
    @PostMapping("/auth/password/forgot") @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary="Запросить сброс пароля", security={})
    public void forgot(@Valid @RequestBody Forgot request) { actions.forgot(request.email()); }
    @PostMapping("/auth/password/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Установить пароль по одноразовой ссылке", security={})
    public void reset(@Valid @RequestBody Reset request) { actions.reset(request.token(),request.newPassword()); }
    @PostMapping("/auth/email/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Подтвердить новый email", security={})
    public void confirm(@Valid @RequestBody Confirm request) { actions.confirmEmail(request.token()); }
    @PostMapping("/users/me/email-change") @ResponseStatus(HttpStatus.ACCEPTED) @SecurityRequirement(name="bearerAuth")
    @Operation(summary="Запросить смену email")
    public void email(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody EmailChange request) {
        actions.requestEmail(user.id(),request.newEmail(),request.password());
    }
    @PatchMapping("/auth/me/username") @SecurityRequirement(name="bearerAuth")
    @Operation(summary="Изменить логин пользователя")
    public UserResponse username(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody UsernameChange request) {
        return actions.changeUsername(user.id(), request.username());
    }
    public record UsernameChange(@NotBlank @Pattern(regexp="^[a-zA-Z0-9_]{3,32}$") String username) {}
    public record Forgot(@NotBlank @Email @Size(max=255) String email) {}
    public record Reset(@NotBlank @Size(max=255) String token, @NotNull @Size(min=8) @MaxBytes(72) String newPassword) {}
    public record Confirm(@NotBlank @Size(max=255) String token) {}
    public record EmailChange(@NotBlank @Email @Size(max=255) String newEmail, @NotBlank @MaxBytes(72) String password) {}
}
