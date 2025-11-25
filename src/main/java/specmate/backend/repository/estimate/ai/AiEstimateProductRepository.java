package specmate.backend.repository.estimate.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.AiEstimateProduct;

import java.util.List;

public interface AiEstimateProductRepository extends JpaRepository<AiEstimateProduct, Long> {
    List<AiEstimateProduct> findByAiEstimateId(Long aiEstimateId);
}