package specmate.backend.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import specmate.backend.entity.ChatMessage;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {
    private String sender;                   // USER or ASSISTANT
    private String content;                  // 자연어 reply
    private String roomId;
    private Map<String, Object> parsedJson;
    private String messageType;
    private LocalDateTime createdAt;

    public static ChatMessageResponse fromEntity(ChatMessage entity) {
        boolean hasJson = entity.getParsedJson() != null && !entity.getParsedJson().isEmpty();

        return ChatMessageResponse.builder()
                .sender(entity.getSender().name())
                .content(hasJson ? null : entity.getContent())
                .roomId(entity.getChatRoom().getId())
                .parsedJson(hasJson ? entity.getParsedJson() : null)
                .messageType(hasJson ? "ESTIMATE" : "TEXT")
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
