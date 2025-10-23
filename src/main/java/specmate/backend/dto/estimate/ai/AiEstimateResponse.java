package specmate.backend.dto.estimate.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.*;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.EstimateProduct;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEstimateResponse {

    private String id;
    private String chatRoomId;
    private String title;
    private String description;
    private Integer totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductResponse> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductResponse {
        private String id;
        private String name;
        private String type;
        private Integer quantity;
        private Integer unitPrice;
        private Boolean matched;
    }

    /** Entity → DTO 변환 (제품 제외) */
    public static AiEstimateResponse fromEntity(AiEstimate entity) {
        return AiEstimateResponse.builder()
                .id(entity.getId())
                .chatRoomId(entity.getChatRoom() != null ? entity.getChatRoom().getId() : null)
                .title(entity.getTitle())
                .description(entity.getMessage() != null ? entity.getMessage().getContent() : null)
                .totalPrice(entity.getTotalPrice())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** Entity + Product 리스트 → DTO 변환 (엔터티는 그대로, 연관 관계 없이 매핑) */
    public static AiEstimateResponse fromEntityWithProducts(AiEstimate entity, List<EstimateProduct> products) {
        return AiEstimateResponse.builder()
                .id(entity.getId())
                .chatRoomId(entity.getChatRoom() != null ? entity.getChatRoom().getId() : null)
                .title(entity.getTitle())
                .description(entity.getMessage() != null ? entity.getMessage().getContent() : null)
                .totalPrice(entity.getTotalPrice())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .products(products != null
                        ? products.stream()
                        .map(AiEstimateResponse::toProductResponse)
                        .collect(Collectors.toList())
                        : null)
                .build();
    }

    private static ProductResponse toProductResponse(EstimateProduct ep) {
        return ProductResponse.builder()
                .id(ep.getProduct() != null ? ep.getProduct().getId().toString() : null)
                .name(ep.getAiName())
                .type(ep.getProduct() != null ? ep.getProduct().getType() : null)
                .quantity(ep.getQuantity())
                .unitPrice(ep.getUnitPrice())
                .matched(ep.getMatched())
                .build();
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
             mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
             mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("AiEstimateResponse 직렬화 실패", e);
        }
    }
}
