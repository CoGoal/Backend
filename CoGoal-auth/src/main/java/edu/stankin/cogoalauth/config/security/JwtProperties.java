package edu.stankin.cogoalauth.config.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the access tokens this service issues. Secret, issuer and audience must match main-service.
 *
 * @param secret         shared HMAC secret (HS256, at least 32 bytes), the same {@code JWT_SECRET} as in main
 * @param issuer         value of the {@code iss} claim
 * @param audience       value of the {@code aud} claim
 * @param accessTokenTtl lifetime of an access token
 * @param clockSkew      tolerated clock difference when this service validates its own tokens
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotNull @DefaultValue("30s") Duration clockSkew
) {
}
