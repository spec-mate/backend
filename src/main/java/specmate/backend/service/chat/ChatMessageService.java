package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.enums.MessageStatus;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.repository.chat.ChatMessageRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;


    /** 사용자 메시지 저장 */
    public ChatMessage saveUserMessage(ChatRoom room, String content) {
        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.USER)
                .content(content)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return chatMessageRepository.save(msg);
    }

    /** GPT 응답 메시지 저장 */
    public ChatMessage saveAssistantMessage(ChatRoom room, String reply) {
        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(reply)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return chatMessageRepository.save(msg);
    }

    /** 특정 채팅방의 메시지 목록 */
    public List<ChatMessage> getMessages(ChatRoom room) {
        return chatMessageRepository.findByChatRoomOrderByCreatedAtAsc(room);
    }
}
