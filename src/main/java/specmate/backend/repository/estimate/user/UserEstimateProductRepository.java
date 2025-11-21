package specmate.backend.repository.estimate.user;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.UserEstimateProduct;

import java.util.List;

public interface UserEstimateProductRepository extends JpaRepository<UserEstimateProduct, String> {
    List<UserEstimateProduct> findByUserEstimateId(String estimateId);
}