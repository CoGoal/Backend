package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.BusinessRuleException;
import edu.stankin.cogoalauth.exception.NotFoundException;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import edu.stankin.cogoalauth.web.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final Clock clock;
    private final MainProfileOutbox mainProfileOutbox;

    /**
     * Replaces the user's roles. {@code USER} is always kept. The user gets tokens with the new roles
     * at the next refresh; already issued access tokens keep the old roles until they expire.
     *
     * @param actorId the admin making the change
     * @throws BusinessRuleException an admin tries to take the ADMIN role away from themselves
     */
    @Transactional
    public UserResponse changeRoles(Long actorId, Long userId, Set<Role> requested) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));

        EnumSet<Role> roles = EnumSet.of(Role.USER);
        roles.addAll(requested);

        // Protects against locking the last admin out by accident
        if (actorId.equals(userId) && user.getRoles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN)) {
            throw new BusinessRuleException("You cannot remove the ADMIN role from yourself");
        }

        if (!user.getRoles().equals(roles)) {
            log.info("Roles of user {} changed from {} to {} by {}", userId, user.getRoles(), roles, actorId);
            user.setRoles(roles);
            user.setUpdatedAt(clock.instant());
            user.setProfileVersion(user.getProfileVersion() + 1);
            mainProfileOutbox.enqueue(user);
        }
        return userMapper.toResponse(user);
    }
}
