package specmate.backend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatRoom;

import java.util.List;

public interface AiEstimateRepository extends JpaRepository<AiEstimate, String> {
    List<AiEstimate> findAllByChatRoom(ChatRoom room);
}
