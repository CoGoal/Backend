package edu.stankin.cogoalauth.config.security;

import java.util.Set;
import java.util.UUID;

/**
 * Principal built from a validated access token.
 * Inject into controllers with {@code @AuthenticationPrincipal AuthenticatedUser user}.
 *
 * @param id    user id from the {@code sub} claim
 * @param roles {@code roles} claim (without the {@code ROLE_} prefix) as it was when the token was issued
 */
public record AuthenticatedUser(Long id, Set<String> roles) {
}
