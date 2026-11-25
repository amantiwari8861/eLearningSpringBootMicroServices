package in.iamandroid.authservice.mapper;

import in.iamandroid.authservice.constants.RoleName;
import in.iamandroid.authservice.dto.UserResponse;
import in.iamandroid.authservice.entities.GenericUser;
import in.iamandroid.authservice.entities.Role;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(GenericUser user);

    default RoleName toRoleName(Role role) {
        return role == null ? null : role.getName();
    }
}
