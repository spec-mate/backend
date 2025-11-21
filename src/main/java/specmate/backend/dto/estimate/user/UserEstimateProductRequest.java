package specmate.backend.dto.estimate.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserEstimateProductRequest {
    private Long productId;
    private String category;
    private Integer quantity = 1;
}