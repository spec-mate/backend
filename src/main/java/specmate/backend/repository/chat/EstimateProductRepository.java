package specmate.backend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import specmate.backend.entity.EstimateProduct;

import java.util.List;

@Repository
public interface EstimateProductRepository extends JpaRepository<EstimateProduct, String> {
    List<EstimateProduct> findAllByAiEstimateId(String aiEstimateId);
}
