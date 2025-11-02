package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.entity.ChatRoom;
import specmate.backend.repository.chat.ChatRoomRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatThreadService {

    private final ChatRoomRepository chatRoomRepository;
    private final AssistantRunner assistantRunner;

    /** OpenAI Thread 생성 */
    public String createThread() {
        String threadId = assistantRunner.createThread();
        log.info(" 새 Thread 생성 완료 (threadId={})", threadId);
        return threadId;
    }

    /** 채팅방에 thread가 없으면 새로 생성해서 연결. thread가 이미 존재하면 그대로 반환. */
    @Transactional
    public ChatRoom ensureThread(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다. (roomId=" + roomId + ")"));

        if (room.getThread() == null || room.getThread().isBlank()) {
            String newThreadId = createThread();
            room.setThread(newThreadId);
            room.setUpdatedAt(LocalDateTime.now());
            chatRoomRepository.save(room);
            log.info("Thread 생성 및 저장 완료 (roomId={}, threadId={})", roomId, newThreadId);
        } else {
            log.debug("Thread 이미 존재 (roomId={}, threadId={})", roomId, room.getThread());
        }

        return room;
    }
}
