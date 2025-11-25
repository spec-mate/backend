package specmate.backend.dto.estimate.ai;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AiEstimateProductResponse {
    private Long id;
    private Long aiEstimateId;
    private String category;
    private String name;
    private Long price;
    private String description;
}