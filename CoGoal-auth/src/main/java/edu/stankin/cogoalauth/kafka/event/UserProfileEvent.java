package edu.stankin.cogoalauth.kafka.event;
import java.time.Instant;
import java.util.UUID;

/** Account snapshot. Name is present only in registration; main owns profile edits. */
public record UserProfileEvent(UUID eventId, int schemaVersion, String eventType,
                               Instant occurredAt, UserProfileSnapshot user) {
    public static UserProfileEvent of(UUID eventId, Instant occurredAt, UserProfileSnapshot user) {
        // Pending migration snapshots keep schema 1 until delivered. New account updates omit profile fields.
        int schema = user.version()>1 && user.name()!=null ? 1 : 2;
        return new UserProfileEvent(eventId,schema,user.version()==1 ? "UserRegistered" : "UserProfileUpdated",occurredAt,user);
    }
}
