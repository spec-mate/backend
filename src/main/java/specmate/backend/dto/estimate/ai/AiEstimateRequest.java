package specmate.backend.dto.estimate.ai;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private List<AiEstimateProductRequest> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiEstimateProductRequest {
        @Schema(description = "DB Product ID", example = "123")
        private String productId;

        @Schema(description = "제품 이름", example = "AMD Ryzen 9 5900X")
        private String name;

        @Schema(description = "제품 카테고리 (cpu, vga, ram 등)", example = "cpu")
        private String type;

        @Schema(description = "수량", example = "1")
        private Integer quantity;

        @Schema(description = "단가", example = "600000")
        private Integer unitPrice;

        @Schema(description = "총 가격 (단가 x 수량)", example = "600000")
        private Integer totalPrice;
    }
}
