package specmate.backend.repository.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import specmate.backend.entity.ChatRoom;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findByUserIdOrderByUpdatedAtDesc(String userId);

}
