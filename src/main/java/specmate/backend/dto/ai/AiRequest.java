package specmate.backend.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequest {
    private String user_input; // FastAPI에서 받는 필드명 (user_input)
    private String thread_id;  // 채팅방 ID (Session 관리용)
}