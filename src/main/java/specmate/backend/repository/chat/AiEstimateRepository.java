package specmate.backend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatRoom;

import java.util.List;
import java.util.UUID;

public interface AiEstimateRepository extends JpaRepository<AiEstimate, String> {

    void deleteAllByChatRoom(ChatRoom room);
}