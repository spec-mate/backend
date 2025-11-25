package specmate.backend.dto.estimate.ai;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiEstimateProductRequest {
    private String category;
    private String name;
    private Long price;
    private String image;
    private String description;
}
