package edu.stankin.cogoalauth.exception;

/**
 * Wrong credentials or an unusable refresh token. Mapped to 401.
 * <p>
 * Services that record something before throwing it (failed login counter, revoked token family)
 * declare {@code noRollbackFor} this exception, so the record is committed.
 */
public class AuthenticationFailedException extends RuntimeException {

    public static final String INVALID_CREDENTIALS = "Неверный email, логин или пароль";
    public static final String INVALID_REFRESH_TOKEN = "Refresh-токен недействителен или истёк";

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
