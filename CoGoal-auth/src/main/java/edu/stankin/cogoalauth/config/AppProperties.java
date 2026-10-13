package edu.stankin.cogoalauth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Application settings under the {@code app} prefix.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull RefreshToken refreshToken,
        @Valid @NotNull Login login,
        @Valid @NotNull Outbox outbox,
        @Valid @NotNull Cors cors,
        @Valid @NotNull BootstrapAdmin bootstrapAdmin
) {

    /**
     * @param ttl             lifetime of a refresh token
     * @param cleanupInterval how often expired refresh tokens are deleted
     */
    public record RefreshToken(@NotNull Duration ttl, @NotNull Duration cleanupInterval) {
    }

    /**
     * @param maxFailedAttempts wrong passwords in a row that lock the account
     * @param lockDuration      how long the account stays locked
     */
    public record Login(@Positive int maxFailedAttempts, @NotNull Duration lockDuration) {
    }

    /**
     * @param pollInterval   how often due tasks are looked up
     * @param batchSize      tasks taken per run
     * @param initialBackoff delay after the first failed attempt; doubles with every next one
     * @param maxBackoff     upper bound of the delay
     * @param lease          a claimed task is not taken again for this long
     */
    public record Outbox(
            @NotNull Duration pollInterval,
            @Positive int batchSize,
            @NotNull Duration initialBackoff,
            @NotNull Duration maxBackoff,
            @NotNull Duration lease
    ) {
    }

    /**
     * @param allowedOrigins origins allowed to call the API from a browser; empty disables CORS
     */
    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }

    /**
     * First administrator, created on startup when email and password are set and the user does not exist.
     */
    public record BootstrapAdmin(String email, String password, @DefaultValue("admin") String username) {

        public boolean configured() {
            return hasText(email) || hasText(password);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
