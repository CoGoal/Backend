package edu.stankin.cogoalauth.web;

import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.exception.BusinessRuleException;
import edu.stankin.cogoalauth.exception.NotFoundException;
import edu.stankin.cogoalauth.service.UserAdminService;
import edu.stankin.cogoalauth.support.TestTokens;
import edu.stankin.cogoalauth.support.WebLayerTest;
import edu.stankin.cogoalauth.web.controllers.admin.AdminUserController;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest(AdminUserController.class)
class AdminUserControllerWebTest {

    private static final Long ADMIN_ID = edu.stankin.cogoalauth.support.TestData.nextId();
    private static final Long USER_ID = edu.stankin.cogoalauth.support.TestData.nextId();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserAdminService userAdminService;

    private ResultActions changeRoles(Long target, String token, String body) throws Exception {
        var request = put("/api/v1/auth/admin/users/{id}/roles", target)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
        }
        return mvc.perform(request);
    }

    @Test
    void adminGrantsAdminRole() throws Exception {
        given(userAdminService.changeRoles(ADMIN_ID, USER_ID, Set.of(Role.USER, Role.ADMIN)))
                .willReturn(new UserResponse(USER_ID, "bob@example.com", "bob", List.of("USER", "ADMIN"), Instant.now()));

        changeRoles(USER_ID, TestTokens.admin(ADMIN_ID), """
                {"roles":["USER","ADMIN"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[1]").value("ADMIN"));
    }

    @Test
    void plainUserGets403() throws Exception {
        changeRoles(USER_ID, TestTokens.user(ADMIN_ID), """
                {"roles":["USER","ADMIN"]}""")
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Недостаточно прав"));
        verifyNoInteractions(userAdminService);
    }

    @Test
    void anonymousGets401() throws Exception {
        changeRoles(USER_ID, null, """
                {"roles":["USER"]}""")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removingOwnAdminRoleIs409() throws Exception {
        given(userAdminService.changeRoles(eq(ADMIN_ID), eq(ADMIN_ID), any()))
                .willThrow(new BusinessRuleException("Запрос конфликтует с текущим состоянием данных"));

        changeRoles(ADMIN_ID, TestTokens.admin(ADMIN_ID), """
                {"roles":["USER"]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Запрос конфликтует с текущим состоянием данных"));
    }

    @Test
    void unknownRoleIs400() throws Exception {
        changeRoles(USER_ID, TestTokens.admin(ADMIN_ID), """
                {"roles":["SUPERUSER"]}""")
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userAdminService);
    }

    @Test
    void missingRolesIs400() throws Exception {
        changeRoles(USER_ID, TestTokens.admin(ADMIN_ID), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("roles"));
    }

    @Test
    void unknownUserIs404() throws Exception {
        given(userAdminService.changeRoles(any(), eq(USER_ID), any()))
                .willThrow(new NotFoundException("User " + USER_ID + " not found"));

        changeRoles(USER_ID, TestTokens.admin(ADMIN_ID), """
                {"roles":["USER"]}""")
                .andExpect(status().isNotFound());
    }
}
