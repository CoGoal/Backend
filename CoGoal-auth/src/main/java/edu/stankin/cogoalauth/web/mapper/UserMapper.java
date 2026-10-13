package edu.stankin.cogoalauth.web.mapper;

import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.entity.enums.Role;
import edu.stankin.cogoalauth.web.dto.auth.UserResponse;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {

    UserResponse toResponse(User user);

    default List<String> roleNames(Set<Role> roles) {
        return Role.sortedNames(roles);
    }
}
