package edu.stankin.cogoalauth.support;

import edu.stankin.cogoalauth.config.AppProperties;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Entities and settings for unit tests.
 */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    private TestData() {
    }

    /** The defaults of application.yml. */
    public static AppProperties properties() {
        return new AppProperties(
                new AppProperties.RefreshToken(Duration.ofDays(30), Duration.ofHours(1)),
                new AppProperties.Login(5, Duration.ofMinutes(15)),
                new AppProperties.Outbox(Duration.ofSeconds(5), 20, Duration.ofSeconds(10), Duration.ofHours(1),
                        Duration.ofMinutes(1)),
                new AppProperties.Cors(List.of()),
                new AppProperties.BootstrapAdmin(null, null, "admin"));
    }

    private static final java.util.concurrent.atomic.AtomicLong IDS = new java.util.concurrent.atomic.AtomicLong(100);
    public static Long nextId() { return IDS.incrementAndGet(); }

    public static User user(Role... roles) {
        User user = new User();
        user.setId(nextId());
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        user.setProfileVersion(1);
        user.setPasswordHash("$2a$10$hash");
        EnumSet<Role> all = EnumSet.of(Role.USER);
        all.addAll(List.of(roles));
        user.setRoles(all);
        user.setCreatedAt(NOW.minus(Duration.ofDays(1)));
        user.setUpdatedAt(NOW.minus(Duration.ofDays(1)));
        return user;
    }
}
