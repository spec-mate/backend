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

    private Long productId;
    private String productName;
    private String image;

    private String category;
    private Integer quantity;

    private Long unitPrice;
    private Long totalPrice;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}