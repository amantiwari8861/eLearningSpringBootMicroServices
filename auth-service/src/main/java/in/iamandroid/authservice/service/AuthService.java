package in.iamandroid.authservice.service;

import in.iamandroid.authservice.constants.RoleName;
import in.iamandroid.authservice.dto.RegisterRequest;
import in.iamandroid.authservice.dto.UserResponse;
import in.iamandroid.authservice.entities.GenericUser;
import in.iamandroid.authservice.entities.Role;
import in.iamandroid.authservice.exception.EmailAlreadyExistsException;
import in.iamandroid.authservice.exception.ResourceNotFoundException;
import in.iamandroid.authservice.mapper.UserMapper;
import in.iamandroid.authservice.repository.RoleRepository;
import in.iamandroid.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("An account with that email already exists");
        }

        GenericUser user = new GenericUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.getRoles().add(getOrCreateRole(RoleName.ROLE_TRAINEE));

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Role getOrCreateRole(RoleName name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(name);
                    return roleRepository.save(role);
                });
    }
}
