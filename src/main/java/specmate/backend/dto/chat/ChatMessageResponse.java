package specmate.backend.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {
    private String sender;     // USER or ASSISTANT
    private String content;
    private String roomId;
    private Map<String, Object> parsedJson;
    private LocalDateTime createdAt;
}

