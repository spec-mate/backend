package specmate.backend.dto.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
@JsonPropertyOrder({ "isJson", "data" })
public class ChatMessageResponse {

    @JsonIgnore
    private String sender;            // USER or ASSISTANT

    @JsonIgnore
    private String content;           // 자연어 reply

    @JsonIgnore
    private String roomId;

    @JsonIgnore
    private Map<String, Object> parsedJson;

    @JsonIgnore
    private String messageType;

    @JsonIgnore
    private LocalDateTime createdAt;

    @JsonProperty("isJson")
    public boolean getIsJson() {
        return "ESTIMATE".equalsIgnoreCase(messageType);
    }

    @JsonProperty("data")
    public Object getData() {
        return getIsJson() ? parsedJson : content;
    }

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
