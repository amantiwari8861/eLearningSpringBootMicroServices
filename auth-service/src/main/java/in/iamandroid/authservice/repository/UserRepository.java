package in.iamandroid.authservice.repository;

import in.iamandroid.authservice.entities.GenericUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<GenericUser, UUID> {

    Optional<GenericUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
