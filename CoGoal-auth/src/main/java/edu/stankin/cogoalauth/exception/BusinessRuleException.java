package edu.stankin.cogoalauth.exception;

/**
 * The operation conflicts with a business rule or the current state (email taken, own admin role, ...).
 * Mapped to 409.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
