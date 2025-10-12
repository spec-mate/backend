package specmate.backend.repository.chat;

import org.apache.catalina.User;
import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.enums.SenderType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByChatRoomOrderByCreatedAtAsc(ChatRoom chatRoom);
    void deleteAllByChatRoom(ChatRoom chatRoom);
}