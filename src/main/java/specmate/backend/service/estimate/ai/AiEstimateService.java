package specmate.backend.service.estimate.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.estimate.ai.AiEstimateProductRequest;
import specmate.backend.dto.estimate.ai.AiEstimateProductResponse;
import specmate.backend.dto.estimate.ai.AiEstimateRequest;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.AiEstimateProduct;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.User;
import specmate.backend.repository.chat.ChatRoomRepository;
import specmate.backend.repository.estimate.ai.AiEstimateProductRepository;
import specmate.backend.repository.estimate.ai.AiEstimateRepository;
import specmate.backend.repository.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiEstimateService {

    private final AiEstimateRepository aiEstimateRepository;
    private final AiEstimateProductRepository aiEstimateProductRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;

    /** AI 견적 생성 */
    @Transactional
    public AiEstimateResponse createEstimate(AiEstimateRequest req) {
        User user = userRepository.findById(req.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom chatRoom = chatRoomRepository.findById(req.getChatRoomId())
            .orElseThrow(() -> new RuntimeException("ChatRoom not found"));

        AiEstimate estimate = AiEstimate.builder()
            .user(user)
            .chatRoom(chatRoom)
            .intent(req.getIntent())
            .intro(req.getIntro())
            .note(req.getNote())
            .totalPrice(0L)
            .createdAt(LocalDateTime.now())
            .build();

        return toEstimateResponse(aiEstimateRepository.save(estimate));
    }

    /** AI 견적에 제품 추가 */
    @Transactional
    public AiEstimateProductResponse addProductToEstimate(Long estimateId, AiEstimateProductRequest req, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
            .orElseThrow(() -> new RuntimeException("AI Estimate not found"));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        AiEstimateProduct product = AiEstimateProduct.builder()
            .aiEstimate(estimate)
            .category(req.getCategory())
            .name(req.getName())
            .price(req.getPrice() != null ? req.getPrice() : 0L)
            .description(req.getDescription())
            .build();

        long currentTotal = estimate.getTotalPrice() != null ? estimate.getTotalPrice() : 0L;
        estimate.setTotalPrice(currentTotal + product.getPrice());

        return toProductResponse(aiEstimateProductRepository.save(product));
    }

    /** AI 견적 조회 (단일) */
    public AiEstimateResponse getEstimate(Long estimateId, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
            .orElseThrow(() -> new RuntimeException("AI Estimate not found"));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        return toEstimateResponseWithProducts(estimate);
    }

    /** 특정 AI 견적의 제품 조회 */
    public List<AiEstimateProductResponse> getEstimateProducts(Long estimateId) {
        return aiEstimateProductRepository.findByAiEstimateId(estimateId)
            .stream()
            .map(this::toProductResponse)
            .collect(Collectors.toList());
    }

    /** 사용자의 모든 AI 견적 조회 */
    public List<AiEstimateResponse> getUserEstimates(String userId) {
        return aiEstimateRepository.findAll()
            .stream()
            .filter(estimate -> estimate.getUser().getId().equals(userId))
            .map(this::toEstimateResponse)
            .collect(Collectors.toList());
    }

    /** AI 견적에서 제품 삭제 */
    @Transactional
    public void removeProductFromEstimate(Long productId, String userId) {
        AiEstimateProduct product = aiEstimateProductRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found"));

        AiEstimate estimate = product.getAiEstimate();

        if (!estimate.getUser().getId().equals(userId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        long currentTotal = estimate.getTotalPrice() != null ? estimate.getTotalPrice() : 0L;
        long productPrice = product.getPrice() != null ? product.getPrice() : 0L;
        estimate.setTotalPrice(Math.max(currentTotal - productPrice, 0L));

        aiEstimateProductRepository.delete(product);
    }

    /** AI 견적 삭제 */
    @Transactional
    public void deleteEstimate(Long estimateId, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
            .orElseThrow(() -> new RuntimeException("AI Estimate not found"));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        aiEstimateRepository.delete(estimate);
    }

    /** 변환 메서드 */
    private AiEstimateResponse toEstimateResponse(AiEstimate entity) {
        return AiEstimateResponse.builder()
            .id(entity.getId())
            .userId(entity.getUser().getId())
            .chatRoomId(entity.getChatRoom().getId())
            .intent(entity.getIntent())
            .intro(entity.getIntro())
            .note(entity.getNote())
            .totalPrice(entity.getTotalPrice())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private AiEstimateResponse toEstimateResponseWithProducts(AiEstimate entity) {
        List<AiEstimateProductResponse> products = entity.getProducts()
            .stream()
            .map(this::toProductResponse)
            .collect(Collectors.toList());

        return AiEstimateResponse.builder()
            .id(entity.getId())
            .userId(entity.getUser().getId())
            .chatRoomId(entity.getChatRoom().getId())
            .intent(entity.getIntent())
            .intro(entity.getIntro())
            .note(entity.getNote())
            .totalPrice(entity.getTotalPrice())
            .createdAt(entity.getCreatedAt())
            .products(products)
            .build();
    }

    private AiEstimateProductResponse toProductResponse(AiEstimateProduct entity) {
        return AiEstimateProductResponse.builder()
            .id(entity.getId())
            .aiEstimateId(entity.getAiEstimate().getId())
            .category(entity.getCategory())
            .name(entity.getName())
            .price(entity.getPrice())
            .description(entity.getDescription())
            .build();
    }
}