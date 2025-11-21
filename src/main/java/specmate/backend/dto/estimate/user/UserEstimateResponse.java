package specmate.backend.dto.estimate.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserEstimateResponse {
    private String id;
    private String userId;
    private String title;
    private String description;

    private Long totalPrice;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}