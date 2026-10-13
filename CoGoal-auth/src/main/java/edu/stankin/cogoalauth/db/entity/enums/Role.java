package edu.stankin.cogoalauth.db.entity.enums;

import java.util.Collection;
import java.util.List;

/**
 * Roles as they appear in the {@code roles} claim (main adds the {@code ROLE_} prefix itself).
 * Every user has {@link #USER}.
 */
public enum Role {
    USER,
    ADMIN;

    /** Names in declaration order, e.g. {@code ["USER", "ADMIN"]}: stable output for tokens and responses. */
    public static List<String> sortedNames(Collection<Role> roles) {
        return roles.stream().sorted().map(Role::name).toList();
    }
}
