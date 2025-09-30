package specmate.backend.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByNickname(String nickname);
}
