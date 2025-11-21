package specmate.backend.repository.estimate.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.AiEstimate;

public interface AiEstimateRepository extends JpaRepository<AiEstimate, Long> {

}
