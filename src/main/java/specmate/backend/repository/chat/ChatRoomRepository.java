package specmate.backend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.User;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
    Optional<ChatRoom> findByUser(User user);

    List<ChatRoom> findAllByUser(User user);
}
