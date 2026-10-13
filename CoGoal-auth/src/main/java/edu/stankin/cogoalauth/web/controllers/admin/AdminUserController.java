package edu.stankin.cogoalauth.web.controllers.admin;

import edu.stankin.cogoalauth.config.OpenApiConfig;
import edu.stankin.cogoalauth.config.security.AuthenticatedUser;
import edu.stankin.cogoalauth.config.security.SecurityConfig;
import edu.stankin.cogoalauth.service.UserAdminService;
import edu.stankin.cogoalauth.web.dto.admin.ChangeRolesRequest;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Only for ROLE_ADMIN (checked in {@link SecurityConfig} by path).
 */
@RestController
@RequestMapping(SecurityConfig.API_PREFIX + "/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin: users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminUserController {

    private final UserAdminService userAdminService;

    @PutMapping("/{id}/roles")
    @Operation(summary = "Grant or revoke ADMIN",
            description = "The body is the complete set of roles; USER is always kept. "
                    + "You cannot remove ADMIN from yourself (409). The user gets the new roles at the next refresh.")
    public UserResponse changeRoles(@AuthenticationPrincipal AuthenticatedUser admin,
                                    @PathVariable Long id,
                                    @Valid @RequestBody ChangeRolesRequest request) {
        return userAdminService.changeRoles(admin.id(), id, request.roles());
    }
}
