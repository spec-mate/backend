package specmate.backend.repository.estimate.user;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.UserEstimate;

import java.util.List;

public interface UserEstimateRepository extends JpaRepository<UserEstimate, String> {
    List<UserEstimate> findByUserId(String userId);
}
