package specmate.backend.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import specmate.backend.config.JwtTokenProvider;
import specmate.backend.dto.aiestimate.EstimateResult;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.dto.chatroom.ChatRoomRequest;
import specmate.backend.dto.chatroom.ChatRoomResponse;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.MessageStatus;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.chat.ChatMessageRepository;
import specmate.backend.repository.chat.ChatRoomRepository;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProductRepository productRepository;
    private final AiEstimateService aiEstimateService;
    private final OpenAIService openAIService;

    /** REST API용 - userId 직접 받음 */
    @Transactional
    public GPTResponse processUserPrompt(String userId, String prompt) throws IOException {
        // 1) 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 2) 채팅방 가져오기
        ChatRoom room = chatRoomRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .user(user)
                                .title("새 채팅방")
                                .createdAt(LocalDateTime.now())
                                .build()
                ));

        // thread 생성
        if (room.getThread() == null) {
            String newThread = openAIService.createThread();
            room.setThread(newThread);
            chatRoomRepository.save(room);
        }

        // 유저 메시지 저장
        saveUserMessage(room, prompt);

        // GPT 호출
        GPTResponse gptResponse = openAIService.callGptApi(prompt, productRepository.findAll(), room.getThread());

        EstimateResult estimateResult = null;
        try {
            estimateResult = new EstimateResultProcessor(new ObjectMapper()).parse(gptResponse.getMessage());
        } catch (Exception e) {
            log.warn("GPT 응답 파싱 실패, 기본 제목 사용", e);
        }

        // 어시스턴트 메시지 저장
        ChatMessage assistantMessage = saveAssistantMessage(room, gptResponse.getMessage());
        aiEstimateService.createEstimate(room, assistantMessage, gptResponse.getMessage());

        // 채팅방 최신화
        if (estimateResult != null && estimateResult.getBuildName() != null) {
            room.setTitle(estimateResult.getBuildName());
        } else {
            room.setTitle("AI 응답"); // fallback
        }
        room.setLastMessage(assistantMessage.getContent());
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        return gptResponse;
    }


    /** WebSocket용 - token 받아서 userId 추출 */
    public GPTResponse processUserPromptWithToken(String token, String prompt) throws IOException {
        String userId = jwtTokenProvider.getUserId(token);
        return processUserPrompt(userId, prompt);
    }

    /** 유저 메시지 저장 */
    public ChatMessage saveUserMessage(ChatRoom room, String content) {
        ChatMessage userMessage = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.USER)
                .content(content)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return chatMessageRepository.save(userMessage);
    }

    /** 어시스턴트 메시지 저장 */
    public ChatMessage saveAssistantMessage(ChatRoom room, String content) {
        ChatMessage assistantMessage = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(content)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return chatMessageRepository.save(assistantMessage);
    }

    /** 유저의 채팅방 목록 조회 */
    public List<ChatRoomResponse> getUserChatRooms(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        return chatRoomRepository.findAllByUser(user).stream()
                .map(ChatRoomResponse::fromEntity)
                .toList();
    }

//    public List<ChatMessageResponse> getChatMessages(String roomId) {
//        ChatRoom room = chatRoomRepository.findById(roomId)
//                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
//
//        return chatMessageRepository.findAllByChatRoom(room).stream()
//                .map(msg -> ChatMessageResponse.builder()
//                        .sender(msg.getSender().name()) // enum → String
//                        .content(msg.getContent())
//                        .roomId(room.getId())
//                        .createdAt(msg.getCreatedAt())
//                        .build())
//                .toList();
//    }

    /** 채팅방 삭제 */
    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
        chatRoomRepository.delete(room);
    }

    /** thread 기반 채팅방 조회 */
    public ChatRoom getChatRoomByThread(String threadId) {
        return chatRoomRepository.findByThread(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread ID에 해당하는 채팅방이 없습니다."));
    }

    /** 채팅방 단일 조회 */
    @Transactional
    public ChatRoomResponse getChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
        return ChatRoomResponse.fromEntity(room);
    }

    /** 채팅방 수정 */
    @Transactional
    public ChatRoomResponse updateChatRoom(String roomId, ChatRoomRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));

        room.setTitle(request.getTitle());
        room.setUpdatedAt(LocalDateTime.now());

        ChatRoom updatedRoom = chatRoomRepository.save(room);
        return ChatRoomResponse.fromEntity(updatedRoom);
    }
}
