package edu.stankin.cogoalauth.web.dto.auth;

import edu.stankin.cogoalauth.web.dto.validation.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @MaxBytes(72) String currentPassword,
        @NotNull @Size(min = 8) @MaxBytes(72) String newPassword
) {

    @Override
    public String toString() {
        return "ChangePasswordRequest(***)";
    }
}
