package edu.stankin.cogoalmain.kafka.event;
import edu.stankin.cogoalmain.pojo.dto.ProfileSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record UserProfileEvent(@NotNull UUID eventId, @Min(1) @Max(2) int schemaVersion,
        @NotNull @Pattern(regexp="UserRegistered|UserProfileUpdated") String eventType,
        @NotNull Instant occurredAt, @NotNull @Valid ProfileSnapshot user) {}
