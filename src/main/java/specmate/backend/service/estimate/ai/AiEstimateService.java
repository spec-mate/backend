package specmate.backend.service.estimate.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.UserAction;
import specmate.backend.repository.chat.*;
import specmate.backend.repository.embedding.ProductEmbeddingRepository;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEstimateService {

    private final AiEstimateRepository aiEstimateRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final ProductEmbeddingRepository embeddingRepository;
    private final ProductRepository productRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    /* ChatRoom → MyPage 견적 확정 저장 ('저장하기' 버튼 클릭 시) */
    @Transactional
    public AiEstimateResponse finalizeEstimate(String roomId, String userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("ChatRoom을 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User를 찾을 수 없습니다."));

        AiEstimate latestEstimate = aiEstimateRepository.findTopByChatRoomOrderByCreatedAtDesc(room)
                .orElseThrow(() -> new IllegalStateException("임시 견적이 존재하지 않습니다."));

        if (latestEstimate.getUserAction() == UserAction.SAVED) {
            log.info("이미 저장된 견적입니다. id={}", latestEstimate.getId());
            return AiEstimateResponse.fromEntityWithProducts(
                    latestEstimate,
                    estimateProductRepository.findAllByAiEstimateId(latestEstimate.getId())
            );
        }

        latestEstimate.setUser(user);
        latestEstimate.setUserAction(UserAction.SAVED);
        latestEstimate.setUpdatedAt(LocalDateTime.now());
        aiEstimateRepository.save(latestEstimate);

        List<EstimateProduct> products =
                estimateProductRepository.findAllByAiEstimateId(latestEstimate.getId());

        log.info("AI 견적 확정 저장 완료: roomId={}, estimateId={}", room.getId(), latestEstimate.getId());
        return AiEstimateResponse.fromEntityWithProducts(latestEstimate, products);
    }

    /* AI가 자동 생성한 견적 엔티티 생성 */
    @Transactional
    public AiEstimate createAiEstimate(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null) throw new IllegalArgumentException("EstimateResult 데이터가 없습니다.");

        AiEstimate aiEstimate = AiEstimate.builder()
                .chatRoom(room)
                .message(assistantMsg)
                .title(result.getBuildName())
                .totalPrice(parsePrice(result.getTotalPrice()))
                .userAction(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return aiEstimateRepository.save(aiEstimate);
    }

    /* 견적에 포함된 제품들을 EstimateProduct 테이블에 저장 */
    @Transactional
    public void saveEstimateProducts(AiEstimate estimate, EstimateResult result) {
        if (estimate == null || result == null || result.getProducts() == null) return;

        for (EstimateResult.Product comp : result.getProducts()) {
            EstimateProduct product = EstimateProduct.builder()
                    .aiEstimate(estimate)
                    .type(comp.getType())
                    .aiName(comp.getMatchedName())
                    .matchedName(comp.getMatchedName())
                    .description(comp.getDescription())
                    .unitPrice(parsePrice(comp.getPrice()))
                    .quantity(1)
                    .matched(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            estimateProductRepository.save(product);
        }

        log.info("EstimateProduct {}개 저장 완료 (estimateId={})",
                result.getProducts().size(), estimate.getId());
    }

    /* 기존 견적 상태 변경  */
    @Transactional
    public AiEstimateResponse updateUserAction(String aiEstimateId, UserAction action, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(aiEstimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 AI 견적입니다."));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 견적에 대한 권한이 없습니다.");
        }

        if (action == null) {
            throw new IllegalArgumentException("UserAction 값이 없습니다.");
        }

        log.info("UserAction 업데이트 요청: id={}, user={}, action={}", aiEstimateId, userId, action);

        estimate.setUserAction(action);
        estimate.setUpdatedAt(LocalDateTime.now());
        aiEstimateRepository.saveAndFlush(estimate);

        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(aiEstimateId);
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);
    }

    /* MyPage 견적 목록 조회 */
    @Transactional(readOnly = true)
    public List<AiEstimateResponse> getEstimatesByUser(String userId) {
        return aiEstimateRepository.findByUserId(userId).stream()
                .map(estimate -> {
                    List<EstimateProduct> products =
                            estimateProductRepository.findAllByAiEstimateId(estimate.getId());
                    return AiEstimateResponse.fromEntityWithProducts(estimate, products);
                })
                .collect(Collectors.toList());
    }

    /* 단일 견적 상세 조회 */
    @Transactional(readOnly = true)
    public AiEstimateResponse getEstimateWithProducts(String estimateId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 견적입니다."));
        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(estimateId);
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);
    }

    /* 견적 삭제 */
    @Transactional
    public void deleteAiEstimate(String estimateId, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("AI 견적을 찾을 수 없습니다."));
        if (!estimate.getUser().getId().equals(userId)) {
            throw new SecurityException("삭제 권한이 없습니다.");
        }

        estimateProductRepository.deleteAllByAiEstimateId(estimateId);
        aiEstimateRepository.delete(estimate);
        log.info("AI 견적({}) 및 연결된 제품 삭제 완료.", estimateId);
    }

    /* 유틸 */
    private Integer parsePrice(String priceStr) {
        if (priceStr == null) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
