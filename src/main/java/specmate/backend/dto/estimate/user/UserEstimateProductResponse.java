package specmate.backend.dto.estimate.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserEstimateProductResponse {
    private String id;
    private String estimateId;
    private Integer productId;
    private String productName;
    private String category;
    private Integer quantity;
    private Integer unitPrice;
    private Integer totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
