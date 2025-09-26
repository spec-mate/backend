package specmate.backend.dto.aiestimate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEstimateCreateRequest {
    private String chatRoomId;   // 채팅방 ID (optional)
    private String prompt;       // 유저 입력
}
