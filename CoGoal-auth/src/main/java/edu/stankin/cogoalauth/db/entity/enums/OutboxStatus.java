package edu.stankin.cogoalauth.db.entity.enums;

public enum OutboxStatus {
    /** Waiting to be sent (first time or retry after {@code nextAttemptAt}). */
    PENDING,
    /** main answered 201 or 200: the profile exists. */
    DONE,
    /** main refused in a way a retry cannot fix (409 email taken by another id, 400). Needs a human. */
    NEEDS_ATTENTION
}
