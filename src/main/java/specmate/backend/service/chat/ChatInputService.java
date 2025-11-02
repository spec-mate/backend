package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.entity.ChatRoom;

@Service
@RequiredArgsConstructor
public class ChatInputService {
    private final ChatThreadService chatThreadService;
    private final ChatMessageService chatMessageService;

    @Transactional
    public ChatRoom handleUserInput(String roomId, String userInput) {
        ChatRoom room = chatThreadService.ensureThread(roomId);
        chatMessageService.saveUserMessage(room, userInput);
        return room;
    }
}
