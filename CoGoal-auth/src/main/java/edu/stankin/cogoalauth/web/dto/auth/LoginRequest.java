package edu.stankin.cogoalauth.web.dto.auth;

import edu.stankin.cogoalauth.web.dto.validation.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * login accepts an email or username; a locked account receives 423.
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String login,
        @NotBlank @MaxBytes(72) String password
) {

    @Override
    public String toString() {
        return "LoginRequest(login=" + login + ")";
    }
}
