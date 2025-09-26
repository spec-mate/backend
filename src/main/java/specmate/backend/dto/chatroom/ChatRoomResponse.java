package specmate.backend.dto.chatroom;

import lombok.*;
import specmate.backend.entity.ChatRoom;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomResponse {
    private UUID id;
    private String title;
    private String lastMessage;
    private String thread;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String userNickname;
    private String assistantName;

    public static ChatRoomResponse fromEntity(ChatRoom room) {
        return ChatRoomResponse.builder()
                .id(UUID.fromString(room.getId()))
                .title(room.getTitle())
                .lastMessage(room.getLastMessage())
                .thread(room.getThread())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .userNickname(room.getUser().getNickname())
                .assistantName(room.getAssistant() != null ? room.getAssistant().getName() : null)
                .build();
    }
}
