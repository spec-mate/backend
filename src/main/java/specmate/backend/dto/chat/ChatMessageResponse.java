package specmate.backend.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {
    private String sender;     // USER or ASSISTANT
    private String content;
    private String roomId;
    private LocalDateTime createdAt;
}

