package specmate.backend.dto.estimate.ai;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEstimateRequest {
    private String chatRoomId;          // AI 견적이 생성된 채팅방 ID
    private String title;
    private String description;
    private Integer totalPrice;
    private List<ProductRequest> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductRequest {
        private String productId;
        private String name;
        private String type;
        private Integer quantity;
        private Integer unitPrice;
        private Integer totalPrice;
    }
}
