package specmate.backend.dto.estimate.ai;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class AiEstimateResponse {
    private Long id;
    private String userId;
    private Long chatRoomId;
    private String intent;
    private String intro;
    private String note;
    private Long totalPrice;
    private LocalDateTime createdAt;

    private List<AiEstimateProductResponse> products;
}