package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.chatroom.ChatRoomRequest;
import specmate.backend.dto.chatroom.ChatRoomResponse;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.User;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.repository.chat.ChatMessageRepository;
import specmate.backend.repository.chat.ChatRoomRepository;
import specmate.backend.repository.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiEstimateRepository aiEstimateRepository;
    private final UserRepository userRepository;
    private final ChatThreadService chatThreadService;

    /** 채팅방 생성 */
    @Transactional
    public ChatRoomResponse createChatRoom(String userId, String title) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        ChatRoom room = ChatRoom.builder()
                .user(user)
                .title(title != null ? title : "새 채팅방")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatRoomRepository.save(room);

        // Thread 생성
        String threadId = chatThreadService.createThread();
        room.setThread(threadId);
        chatRoomRepository.save(room);

        return ChatRoomResponse.fromEntity(room);
    }

    /** 내 모든 채팅방 조회 */
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getUserChatRooms(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        return chatRoomRepository.findByUserOrderByCreatedAtDesc(user)
                .stream().map(ChatRoomResponse::fromEntity).toList();
    }

    /** 특정 채팅방 조회 */
    @Transactional(readOnly = true)
    public ChatRoomResponse getChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        return ChatRoomResponse.fromEntity(room);
    }


    /** 채팅방 엔티티 직접 반환 */
    @Transactional(readOnly = true)
    public ChatRoom getEntityById(String roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
    }

    /** 채팅방 이름 수정 */
    @Transactional
    public ChatRoomResponse updateChatRoom(String roomId, ChatRoomRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        room.setTitle(request.getTitle());
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);
        return ChatRoomResponse.fromEntity(room);
    }

    /** 채팅방 삭제 (메시지 + 견적도 함께 삭제) */
    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        chatMessageRepository.deleteAllByChatRoom(room);
        aiEstimateRepository.deleteAllByChatRoom(room);
        chatRoomRepository.delete(room);
    }

    /** 특정 채팅방의 메시지 목록 조회 */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatMessages(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        return chatMessageRepository.findByChatRoomOrderByCreatedAtAsc(room)
                .stream().map(ChatMessageResponse::fromEntity).toList();
    }

    /** ChatRoom 저장 (Thread ID 업데이트 등) */
    @Transactional
    public void saveRoom(ChatRoom room) {
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);
    }
}
