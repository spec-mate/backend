package specmate.backend.dto.estimate.ai;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiEstimateRequest {
    private String userId;
    private Long chatRoomId;
    private String intent;
    private String intro;
    private String note;
}