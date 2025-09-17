package specmate.backend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findAllByChatRoom(ChatRoom room);
}