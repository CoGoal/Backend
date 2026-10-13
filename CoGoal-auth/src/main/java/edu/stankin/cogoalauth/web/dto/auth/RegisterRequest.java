package edu.stankin.cogoalauth.web.dto.auth;

import edu.stankin.cogoalauth.web.dto.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/**
 * Email and username have the same limits as main's {@code InternalUserRequest}, so main accepts the profile.
 *
 * @param email    stored in lower case; login accepts email or username
 * @param username unique ignoring case
 * @param password 8 characters to 72 bytes (the BCrypt limit)
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$") String username,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Size(min = 8) @MaxBytes(72) String password
) {

    public RegisterRequest(String email, String username, String password) {
        this(email, username, username, password);
    }

    // Never print the password, e.g. in a debug log of request arguments
    @Override
    public String toString() {
        return "RegisterRequest(email=" + email + ", username=" + username + ")";
    }
}
