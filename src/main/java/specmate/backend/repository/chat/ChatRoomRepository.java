package specmate.backend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
//    Optional<ChatRoom> findByUser(User user);

    List<ChatRoom> findAllByUser(User user);

    Optional<ChatRoom> findFirstByUserOrderByCreatedAtDesc(User user);
//    Optional<ChatRoom> findByThread(String thread);
}
