package edu.stankin.cogoalauth.exception;
import java.time.Instant;
public class AccountLockedException extends AuthenticationFailedException {
    public AccountLockedException(Instant until) { super("Слишком много попыток входа. Попробуйте после " + until); }
}
