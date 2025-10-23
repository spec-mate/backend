package specmate.backend.service.estimate.ai;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.EstimateProduct;
import specmate.backend.entity.Product;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.repository.chat.EstimateProductRepository;
import specmate.backend.repository.product.ProductRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEstimateService {

    private final EstimateProductRepository estimateProductRepository;
    private final ProductRepository productRepository;
    private final AiEstimateRepository aiEstimateRepository;

    /** ChatService에서 호출하는 AI 견적 생성 메서드 */
    @Transactional
    public AiEstimate createAiEstimate(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null) {
            throw new IllegalArgumentException("EstimateResult가 null입니다.");
        }

        AiEstimate aiEstimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .message(assistantMsg)
                .title(result.getBuildName() != null ? result.getBuildName() : "이름 없는 견적")
                .totalPrice(parsePrice(result.getTotalPrice()))
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // DB 저장
        aiEstimateRepository.save(aiEstimate);

        // 제품 매핑 저장
        saveEstimateProducts(aiEstimate, result);

        return aiEstimate;
    }

    /** EstimateProduct 저장 (AiEstimate와 Product 매핑) */
    @Transactional
    public void saveEstimateProducts(AiEstimate aiEstimate, EstimateResult result) {
        if (result == null || result.getProducts() == null) return;

        for (EstimateResult.Product comp : result.getProducts()) {
            Product matchedProduct = null;

            // ID 기반 매핑
            if (comp.getId() != null && !comp.getId().isBlank()) {
                try {
                    Integer pid = Integer.valueOf(comp.getId());
                    matchedProduct = productRepository.findById(pid).orElse(null);
                } catch (NumberFormatException ignored) {}
            }

            // 이름 기반 fallback
            if (matchedProduct == null && comp.getName() != null) {
                matchedProduct = productRepository
                        .findFirstByNameContainingIgnoreCase(comp.getName())
                        .orElse(null);
            }

            // 매칭 실패 시 skip
            if (matchedProduct == null) {
                log.warn("AI가 추천한 '{}' 제품을 DB에서 찾지 못했습니다. 매핑 생략.", comp.getName());
                continue;
            }

            // EstimateProduct 생성
            EstimateProduct entity = EstimateProduct.builder()
                    .aiEstimate(aiEstimate)
                    .product(matchedProduct)
                    .aiName(comp.getName())
                    .matched(matchedProduct != null)
                    .quantity(1)
                    .unitPrice(parsePrice(comp.getPrice()))
                    .createdAt(LocalDateTime.now())
                    .build();

            estimateProductRepository.save(entity);
        }
    }

    /** 사용자별 견적 목록 조회 */
    @Transactional
    public List<AiEstimateResponse> getEstimatesByUser(String userId) {
        List<AiEstimate> estimates = aiEstimateRepository.findByUserId(userId);

        return estimates.stream()
                .map(estimate -> {
                    var products = estimateProductRepository.findAllByAiEstimateId(estimate.getId());
                    return AiEstimateResponse.fromEntityWithProducts(estimate, products);
                })
                .collect(Collectors.toList());
    }


    /** 견적 상세 조회 (상품 포함) */
    @Transactional
    public AiEstimateResponse getEstimateWithProducts(String estimateId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 견적입니다."));

        // 관련 제품 조회 (별도 Repository 사용)
        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(estimateId);

        // Entity + Products 를 DTO로 변환
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);

    }

    private Integer parsePrice(String priceStr) {
        if (priceStr == null) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    @Transactional
    public void deleteAiEstimate(String estimateId, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("AI 견적을 찾을 수 없습니다."));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new SecurityException("삭제 권한이 없습니다.");
        }

        // 견적에 속한 제품도 함께 삭제
        estimateProductRepository.deleteAllByAiEstimateId(String.valueOf(estimate));
        aiEstimateRepository.delete(estimate);
    }
}
