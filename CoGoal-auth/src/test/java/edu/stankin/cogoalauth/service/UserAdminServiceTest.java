package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.BusinessRuleException;
import edu.stankin.cogoalauth.exception.NotFoundException;
import edu.stankin.cogoalauth.support.TestData;
import edu.stankin.cogoalauth.web.mapper.UserMapper;
import edu.stankin.cogoalauth.web.mapper.UserMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    private final UserMapper userMapper = new UserMapperImpl();
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userRepository, userMapper, Clock.fixed(TestData.NOW, ZoneOffset.UTC), org.mockito.Mockito.mock(MainProfileOutbox.class));
    }

    @Test
    void grantsAdmin() {
        User user = TestData.user();
        given(userRepository.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));

        var response = service.changeRoles(TestData.nextId(), user.getId(), Set.of(Role.USER, Role.ADMIN));

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(response.roles()).containsExactly("USER", "ADMIN");
        assertThat(user.getUpdatedAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void revokesAdminFromSomeoneElseAndKeepsUser() {
        User user = TestData.user(Role.ADMIN);
        given(userRepository.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));

        service.changeRoles(TestData.nextId(), user.getId(), Set.of());

        assertThat(user.getRoles()).containsExactly(Role.USER);
    }

    @Test
    void adminCannotRevokeOwnAdminRole() {
        User admin = TestData.user(Role.ADMIN);
        given(userRepository.findByIdForUpdate(admin.getId())).willReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.changeRoles(admin.getId(), admin.getId(), Set.of(Role.USER)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(admin.getRoles()).contains(Role.ADMIN);
    }

    @Test
    void adminMayResendOwnRoles() {
        User admin = TestData.user(Role.ADMIN);
        given(userRepository.findByIdForUpdate(admin.getId())).willReturn(Optional.of(admin));

        service.changeRoles(admin.getId(), admin.getId(), Set.of(Role.USER, Role.ADMIN));

        assertThat(admin.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
    }

    @Test
    void unknownUserIs404() {
        Long id = TestData.nextId();
        given(userRepository.findByIdForUpdate(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRoles(TestData.nextId(), id, Set.of(Role.ADMIN)))
                .isInstanceOf(NotFoundException.class);
    }
}
