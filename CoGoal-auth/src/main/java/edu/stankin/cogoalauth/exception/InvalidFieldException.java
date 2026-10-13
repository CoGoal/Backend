package edu.stankin.cogoalauth.exception;

import lombok.Getter;

/**
 * A request field is well-formed but its value is rejected by a service check (e.g. wrong current password).
 * Mapped to 400 with the same {@code errors} list as a bean validation failure.
 */
@Getter
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }
}
