package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import specmate.backend.entity.ChatRoom;
import specmate.backend.repository.chat.ChatRoomRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatThreadService {

    private final ChatRoomRepository chatRoomRepository;
    private final AssistantRunner assistantRunner;

    /** OpenAI API를 호출하여 새 Thread를 생성하고 thread_id를 반환 */
    public String createThread() {
        String threadId = assistantRunner.createThread();
        log.info("새 Thread 생성 완료 (threadId={})", threadId);
        return threadId;
    }

    /** 주어진 roomId의 ChatRoom에 Thread가 없으면 새로 생성해서 연결 */
    public ChatRoom ensureThread(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        if (room.getThread() == null || room.getThread().isBlank()) {
            String threadId = createThread();
            room.setThread(threadId);
            room.setUpdatedAt(LocalDateTime.now());
            chatRoomRepository.save(room);
            log.info("Thread 생성 완료 (roomId={}, threadId={})", roomId, threadId);
        }
        return room;
    }
}
