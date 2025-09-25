package specmate.backend.service.estimate.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.aiestimate.AiEstimateCreateRequest;
import specmate.backend.dto.aiestimate.AiEstimateResponse;
import specmate.backend.dto.aiestimate.AiEstimateUpdateRequest;
import specmate.backend.dto.product.ProductResponse;
import specmate.backend.entity.*;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.repository.chat.AssistantRepository;
import specmate.backend.repository.chat.EstimateProductRepository;
import specmate.backend.repository.product.ProductRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEstimateService {

    private final AiEstimateRepository aiEstimateRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final ProductRepository productRepository;
    private final AssistantRepository assistantRepository;


    @Transactional
    public AiEstimate createEstimate(ChatRoom room, ChatMessage assistantMessage, String gptResponseText) {
        // 1) 기본 Assistant 조회 또는 생성
        Assistant defaultAssistant = assistantRepository.findByName("default")
                .orElseGet(() -> assistantRepository.save(
                        Assistant.builder()
                                .name("default")
                                .description("자동 생성된 기본 어시스턴트")
                                .instruction("기본 프롬프트")
                                .model("gpt-4o-mini")
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        aiEstimateRepository.save(aiEstimate);

        try {
            // 3) GPT 응답 JSON 파싱
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(gptResponseText);
            JsonNode components = root.get("components");

            int totalPriceSum = 0;

            if (components != null && components.isArray()) {
                for (JsonNode comp : components) {
                    String type = comp.has("type") ? comp.get("type").asText() : null;
                    String name = comp.has("name") ? comp.get("name").asText() : null;
                    String priceStr = comp.has("price") ? comp.get("price").asText().replaceAll("[^0-9]", "") : "0";
                    int unitPrice = priceStr.isEmpty() ? 0 : Integer.parseInt(priceStr);

                    // DB에서 상품 조회 (이름 포함 검색)
                    List<Product> candidates = productRepository.findByNameContainingIgnoreCase(name);
                    Product matched = candidates.isEmpty() ? null : candidates.get(0);

                    if (matched == null) {
                        EstimateProduct ep = EstimateProduct.builder()
                                .aiEstimate(aiEstimate)
                                .product(matched)
                                .aiName(name)
                                .matched(matched != null)
                                .similarity(1.0f)
                                .quantity(1)
                                .unitPrice(unitPrice)
                                .totalPrice(unitPrice)
                                .createdAt(LocalDateTime.now())
                                .build();

                        estimateProductRepository.save(ep);
                        totalPriceSum += unitPrice;
                    } else {
                        log.warn("AI 견적에 포함된 '{}' 상품은 DB에 존재하지 않아 저장하지 않습니다.", name);
                    }
                }
            }

            // 4) 총합 업데이트
            aiEstimate.setTotalPrice(totalPriceSum);
            aiEstimate.setUpdatedAt(LocalDateTime.now());
            aiEstimateRepository.save(aiEstimate);

        } catch (Exception e) {
            // 파싱 실패 시 로그만 찍고 넘어감
            log.error("GPT 응답 파싱 실패", e);
        }

        return aiEstimate;
    }


    /** 특정 채팅방의 견적 조회 */
    @Transactional(readOnly = true)
    public List<AiEstimateResponse> getEstimatesByRoomId(UUID roomId) {
        return aiEstimateRepository.findAllByChatRoomId(roomId.toString())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 단일 견적 조회 */
    @Transactional(readOnly = true)
    public AiEstimateResponse getEstimate(String estimateId) {
        AiEstimate aiEstimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 견적입니다."));
        return toResponse(aiEstimate);
    }

    /** AI 견적 수정 */
    @Transactional
    public AiEstimateResponse updateEstimate(String estimateId, AiEstimateUpdateRequest request) {
        AiEstimate aiEstimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 견적입니다."));

        if (request.getTitle() != null) {
            aiEstimate.setTitle(request.getTitle());
        }
        if (request.getStatus() != null) {
            aiEstimate.setStatus(request.getStatus());
        }

        aiEstimateRepository.save(aiEstimate);
        return toResponse(aiEstimate);
    }


    /** AI 견적 삭제 */
    @Transactional
    public void deleteEstimate(String estimateId) {
        if (!aiEstimateRepository.existsById(estimateId)) {
            throw new IllegalArgumentException("존재하지 않는 견적입니다.");
        }
        estimateProductRepository.deleteAllByAiEstimateId(estimateId);
        aiEstimateRepository.deleteById(estimateId);
    }

    /** 엔티티 → DTO 변환 */
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
                .products(products)
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
