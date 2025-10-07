package specmate.backend.dto.aiestimate;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import specmate.backend.dto.product.ProductResponse;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEstimateResponse {
    private String estimateId;
    private String chatRoomId;
    private String messageId;
    private String assistantId;
    private String userId;

    private String title;
    private Integer totalPrice;

    @JsonProperty("components")
    private List<ProductResponse> products; // 견적에 포함된 상품 정보
}
