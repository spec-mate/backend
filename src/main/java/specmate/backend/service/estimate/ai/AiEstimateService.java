package specmate.backend.service.estimate.ai;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.UserAction;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.repository.chat.ChatMessageRepository;
import specmate.backend.repository.chat.EstimateProductRepository;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;
import specmate.backend.repository.chat.ChatRoomRepository;
import specmate.backend.service.product.ProductSearchService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEstimateService {

    private final EstimateProductRepository estimateProductRepository;
    private final ProductRepository productRepository;
    private final AiEstimateRepository aiEstimateRepository;
    private final ProductSearchService productSearchService;

    /** AI가 자동 생성한 견적 저장 */
    @Transactional
    public AiEstimate createAiEstimate(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null) {
            throw new IllegalArgumentException("EstimateResult가 null입니다.");
        }

        AiEstimate aiEstimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .message(assistantMsg)
                .title(Optional.ofNullable(result.getBuildName()).orElse("이름 없는 견적"))
                .totalPrice(parsePrice(result.getTotalPrice()))
                .status("SUCCESS")
                .userAction(UserAction.NONE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        aiEstimateRepository.save(aiEstimate);
        saveEstimateProducts(aiEstimate, result);

        return aiEstimate;
    }
    /** 제품 매핑 (자동 저장 시 사용) */
    @Transactional
    public void saveEstimateProducts(AiEstimate aiEstimate, EstimateResult result) {
        if (result == null || result.getProducts() == null) return;

        for (EstimateResult.Product comp : result.getProducts()) {
            if (comp.getMatchedName() == null || comp.getMatchedName().isBlank()) continue;

            // 임베딩 기반 의미 검색 (GPT가 의미 판단을 이미 수행했으므로 단일 검색)
            Optional<Product> optionalProduct =
                    productSearchService.findMostSimilarProduct(comp.getMatchedName(), comp.getType());

            if (optionalProduct.isPresent()) {
                Product matchedProduct = optionalProduct.get();
                double similarity = productSearchService.getLastSimilarityScore();

                // GPT 의미 매칭에 맞게 임계값 완화 (0.75 → 0.5)
                if (similarity >= 0.5) {
                    log.info("AI 의미 매칭 성공: '{}' → '{}' (유사도={})",
                            comp.getMatchedName(), matchedProduct.getName(), String.format("%.2f", similarity));

                    saveEstimateProduct(aiEstimate, matchedProduct, comp);
                } else {
                    log.debug("유사도 낮음 ({}) → '{}' 매칭 보류",
                            String.format("%.2f", similarity), comp.getMatchedName());
                }
            } else {
                // 매칭 실패 시 GPT의 판단 보존
                log.warn("GPT 의미 매칭 실패: '{}'", comp.getMatchedName());
                EstimateProduct entity = EstimateProduct.builder()
                        .aiEstimate(aiEstimate)
                        .aiName(comp.getMatchedName())
                        .matched(false)
                        .quantity(1)
                        .unitPrice(parsePrice(comp.getPrice()))
                        .createdAt(LocalDateTime.now())
                        .build();
                estimateProductRepository.save(entity);
            }
        }
    }

    private void saveEstimateProduct(AiEstimate aiEstimate, Product product, EstimateResult.Product comp) {


        // GPT가 제안한 이름 (AI 이름)
        String aiSuggestedName = comp.getMatchedName();

        // DB 매칭된 실제 이름 (DB 검색 결과)
        String matchedProductName = product != null ? product.getName() : null;

        EstimateProduct entity = EstimateProduct.builder()
                .aiEstimate(aiEstimate)
                .product(product)
                .aiName(aiSuggestedName)
                .matchedName(matchedProductName)
                .similarityScore(productSearchService.getLastSimilarityScore())
                .matched(product != null)
                .quantity(1)
                .unitPrice(parsePrice(comp.getPrice()))
                .createdAt(LocalDateTime.now())
                .build();

        estimateProductRepository.save(entity);
    }



    /** 사용자 행동(user_action) 업데이트 */
    @Transactional
    public AiEstimateResponse updateUserAction(String aiEstimateId, UserAction action, String userId) {
        // 견적 조회
        AiEstimate estimate = aiEstimateRepository.findById(aiEstimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 AI 견적입니다."));

        // 권한 검증
        if (!estimate.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 견적에 대한 권한이 없습니다.");
        }

        // Enum 검증
        if (action == null) {
            throw new IllegalArgumentException("UserAction 값이 없습니다.");
        }

        // 상태 업데이트
        estimate.setUserAction(action);
        estimate.setUpdatedAt(LocalDateTime.now());

        aiEstimateRepository.save(estimate);

        // 응답 생성
        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(aiEstimateId);
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);
    }


    /** 조회, 삭제 로직 */
    @Transactional
    public List<AiEstimateResponse> getEstimatesByUser(String userId) {
        return aiEstimateRepository.findByUserId(userId)
                .stream()
                .map(estimate -> {
                    List<EstimateProduct> products =
                            estimateProductRepository.findAllByAiEstimateId(estimate.getId());
                    return AiEstimateResponse.fromEntityWithProducts(estimate, products);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public AiEstimateResponse getEstimateWithProducts(String estimateId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 견적입니다."));
        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(estimateId);
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);
    }

    @Transactional
    public void deleteAiEstimate(String estimateId, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("AI 견적을 찾을 수 없습니다."));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new SecurityException("삭제 권한이 없습니다.");
        }

        estimateProductRepository.deleteAllByAiEstimateId(estimate.getId());
        aiEstimateRepository.delete(estimate);

        log.info("AI 견적({}) 및 연결된 제품이 삭제되었습니다.", estimateId);
    }

    /** 가격 파싱 유틸 */
    private Integer parsePrice(String priceStr) {
        if (priceStr == null) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            log.warn("가격 파싱 실패: {}", priceStr);
            return 0;
        }
    }
}
