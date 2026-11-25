package in.iamandroid.apigateway.security;

import java.util.Set;
import java.util.UUID;

public record GatewayIdentity(

        UUID id,
        String email,
        Set<String> roles,
        boolean enabled
) {
}
