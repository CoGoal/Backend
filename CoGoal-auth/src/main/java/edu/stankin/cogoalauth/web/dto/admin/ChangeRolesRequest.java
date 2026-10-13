package edu.stankin.cogoalauth.web.dto.admin;

import edu.stankin.cogoalauth.db.entity.enums.Role;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * @param roles the complete new set of roles, e.g. {@code ["USER", "ADMIN"]} to grant admin
 *              or {@code ["USER"]} to take it away. {@code USER} is always kept even if omitted.
 */
public record ChangeRolesRequest(@NotNull Set<@NotNull Role> roles) {
}
