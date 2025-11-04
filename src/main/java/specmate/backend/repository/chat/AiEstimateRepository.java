package specmate.backend.repository.chat;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.enums.UserAction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiEstimateRepository extends JpaRepository<AiEstimate, String> {
    void deleteAllByChatRoom(ChatRoom room);

    Optional<AiEstimate> findTopByChatRoomOrderByCreatedAtDesc(ChatRoom chatRoom);

    List<AiEstimate> findByUserIdAndUserAction(String userId, UserAction userAction);

    /** 최근 생성된 견적의 estimate_json 컬럼 값만 가져오기 */
    @Query(value = """
        SELECT estimate_json 
        FROM ai_estimate 
        WHERE chat_room_id = :roomId 
        ORDER BY created_at DESC 
        LIMIT 1
    """, nativeQuery = true)
    String findLatestEstimateJson(@Param("roomId") String roomId);
}