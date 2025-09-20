package specmate.backend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import specmate.backend.entity.Assistant;

import java.util.Optional;

@Repository
public interface AssistantRepository extends JpaRepository<Assistant, String> {
    Optional<Assistant> findByName(String name);
}
