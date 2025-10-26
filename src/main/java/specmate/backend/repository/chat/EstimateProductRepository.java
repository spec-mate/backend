package specmate.backend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import specmate.backend.entity.ChatRoom;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.entity.EstimateProduct;

import java.util.List;

@Repository
public interface EstimateProductRepository extends JpaRepository<EstimateProduct, String> {
    // 특정 AiEstimate ID 기준으로 전체 조회
    List<EstimateProduct> findAllByAiEstimateId(String aiEstimateId);

    // 특정 AiEstimate ID 기준으로 전체 삭제
    void deleteAllByAiEstimateId(String aiEstimateId);
}
