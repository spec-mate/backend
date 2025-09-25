package specmate.backend.dto.aiestimate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEstimateUpdateRequest {
    private String title;        // 견적 제목
    private String status;       // 견적 상태
}
