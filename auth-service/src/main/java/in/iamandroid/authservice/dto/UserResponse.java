package in.iamandroid.authservice.dto;

import in.iamandroid.authservice.constants.RoleName;

import java.util.Set;
import java.util.UUID;

public record UserResponse(

        UUID id,
        String email,
        String name,
        Set<RoleName> roles,
        boolean enabled
) {
}
