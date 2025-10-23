package specmate.backend.service.estimate.ai;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.AiEstimateRequest;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.entity.enums.UserAction;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.repository.chat.ChatMessageRepository;
import specmate.backend.repository.chat.EstimateProductRepository;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;
import specmate.backend.repository.chat.ChatRoomRepository;
import specmate.backend.entity.enums.MessageStatus;

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
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

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
            Product matchedProduct = null;

            // 1) ID 매칭
            if (comp.getId() != null && !comp.getId().isBlank()) {
                try {
                    Integer pid = Integer.parseInt(comp.getId());
                    matchedProduct = productRepository.findById(pid).orElse(null);
                } catch (NumberFormatException ignored) {}
            }

            // 2) 이름 기반 매칭
            if (matchedProduct == null && comp.getName() != null) {
                List<Product> similar = productRepository.findSimilarByName(comp.getName());
                if (!similar.isEmpty()) matchedProduct = similar.get(0);
            }

            // 3) Fuzzy 매칭
            if (matchedProduct == null && comp.getName() != null) {
                matchedProduct = productRepository.findMostSimilar(comp.getName()).orElse(null);
            }

            if (matchedProduct == null) {
                log.warn("AI가 추천한 '{}' 제품을 DB에서 찾지 못했습니다.", comp.getName());
                continue;
            }

            log.info("매칭 성공: AI '{}' → DB '{}'", comp.getName(), matchedProduct.getName());

            EstimateProduct entity = EstimateProduct.builder()
                    .aiEstimate(aiEstimate)
                    .product(matchedProduct)
                    .aiName(comp.getName())
                    .matched(true)
                    .quantity(1)
                    .unitPrice(parsePrice(comp.getPrice()))
                    .createdAt(LocalDateTime.now())
                    .build();

            estimateProductRepository.save(entity);
        }
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
