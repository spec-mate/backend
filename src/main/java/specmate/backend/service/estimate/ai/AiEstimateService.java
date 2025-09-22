package specmate.backend.service.estimate.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import specmate.backend.dto.aiestimate.AiEstimateResponse;
import specmate.backend.dto.product.ProductResponse;
import specmate.backend.entity.*;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.repository.chat.AssistantRepository;
import specmate.backend.repository.chat.EstimateProductRepository;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.utils.PriceUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiEstimateService {

    private final AiEstimateRepository aiEstimateRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final AssistantRepository assistantRepository;
    private final ProductRepository productRepository;

    /** AI 견적 생성 */
    public AiEstimate createEstimate(ChatRoom room, ChatMessage assistantMessage, String gptResponseText) {
        // 1) 어시스턴트 조회 or 생성
        Assistant defaultAssistant = assistantRepository.findByName("default")
                .orElseGet(() -> assistantRepository.save(
                        Assistant.builder()
                                .name("default")
                                .description("자동 생성된 기본 어시스턴트")
                                .instruction("기본 프롬프트")
                                .model("gpt-5-mini-2025-08-07")
                                .isActive(true)
                                .build()
                ));

        // 2) AiEstimate 저장
        AiEstimate aiEstimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .assistant(defaultAssistant)
                .message(assistantMessage)
                .title("GPT 추천 견적")
                .status("success")
                .totalPrice(0)
                .build();
        aiEstimateRepository.save(aiEstimate);

        // 3) GPT 응답에서 상품명 추출
        String normalizedName = normalizeProductName(gptResponseText);
        if (normalizedName == null || normalizedName.trim().isEmpty()) {
            normalizedName = "없음";
        }

        // 4) DB 탐색 및 EstimateProduct 저장
        List<Product> candidates = productRepository.findByNameContainingIgnoreCase(normalizedName);

        if (!candidates.isEmpty()) {
            long totalPriceSum = 0L;

            for (Product product : candidates) {
                int unitPrice = Math.toIntExact(PriceUtils.safeParsePrice(product.getLowestPrice()));
                int quantity = 1;
                int totalPrice = unitPrice * quantity;

                EstimateProduct ep = EstimateProduct.builder()
                        .aiEstimate(aiEstimate)
                        .product(product)
                        .aiName(normalizedName)
                        .matched(true)
                        .similarity(1.0f)
                        .quantity(quantity)
                        .unitPrice(unitPrice)
                        .totalPrice(totalPrice)
                        .createdAt(LocalDateTime.now())
                        .build();
                estimateProductRepository.save(ep);

                totalPriceSum += totalPrice;
            }

            // 총액 업데이트 (모든 후보 합산)
            aiEstimate.setTotalPrice((int) totalPriceSum);
            aiEstimateRepository.save(aiEstimate);
        }

        return aiEstimate;
    }

    private String normalizeProductName(String text) {
        return text.replaceAll("[^a-zA-Z0-9가-힣 ]", "")
                .trim()
                .toLowerCase();
    }

    /** 특정 채팅방의 견적 조회 */
    public List<AiEstimate> getEstimates(String roomId, ChatRoom room) {
        return aiEstimateRepository.findAllByChatRoom(room);
    }

    /** 엔티티 → DTO 변환 (Repository 이용) */
    public AiEstimateResponse toResponse(AiEstimate aiEstimate) {
        if (aiEstimate == null) return null;

        List<ProductResponse> products = estimateProductRepository.findAllByAiEstimateId(aiEstimate.getId())
                .stream()
                .map(ep -> toProductResponse(ep.getProduct()))
                .toList();

        return AiEstimateResponse.builder()
                .estimateId(aiEstimate.getId())
                .chatRoomId(aiEstimate.getChatRoom() != null ? aiEstimate.getChatRoom().getId() : null)
                .messageId(aiEstimate.getMessage() != null ? aiEstimate.getMessage().getId() : null)
                .assistantId(aiEstimate.getAssistant() != null ? aiEstimate.getAssistant().getId() : null)
                .userId(aiEstimate.getUser() != null ? aiEstimate.getUser().getId() : null)
                .title(aiEstimate.getTitle())
                .totalPrice(aiEstimate.getTotalPrice())
                .products(products) // Repository로 가져온 값 세팅
                .build();
    }

    private ProductResponse toProductResponse(Product product) {
        if (product == null) return null;

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .image(product.getImage())
                .popRank(product.getPopRank())
                .regDate(product.getRegDate())
                .options(product.getOptions())
                .priceInfo(product.getPriceInfo())
                .lowestPrice(product.getLowestPrice())
                .type(product.getType())
                .manufacturer(product.getManufacturer())
                .build();
    }
}
