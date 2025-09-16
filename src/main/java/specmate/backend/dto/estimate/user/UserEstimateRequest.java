package specmate.backend.dto.estimate.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserEstimateRequest {
    private String userId;
    private String title;
    private String description;
}