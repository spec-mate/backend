package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import specmate.backend.dto.ai.AiComponent;
import specmate.backend.dto.ai.AiRequest;
import specmate.backend.dto.ai.AiResponse;
import specmate.backend.entity.User;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.AiEstimateProduct;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.repository.user.UserRepository;
import specmate.backend.repository.estimate.ai.AiEstimateRepository;
import specmate.backend.repository.chat.ChatMessageRepository;
import specmate.backend.repository.chat.ChatRoomRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiEstimateRepository aiEstimateRepository;
    private final UserRepository userRepository;
    private final WebClient webClient;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    /**
     * 사용자 메시지 처리 및 AI 응답 생성
     */
    @Transactional
    public AiResponse processUserMessage(Long chatRoomId, String userId, String userMessageContent) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
            .orElseThrow(() -> new RuntimeException("ChatRoom not found"));

        saveChatMessage(chatRoom, ChatMessage.Sender.USER, userMessageContent, ChatMessage.MessageType.TALK, null);

        AiRequest request = AiRequest.builder()
            .user_input(userMessageContent)
            .thread_id(String.valueOf(chatRoomId))
            .build();

        AiResponse aiResponse;
        try {
            aiResponse = webClient.post()
                .uri(aiServerUrl + "/chat/message")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiResponse.class)
                .block();
        } catch (Exception e) {
            log.error("AI Server Error", e);
            saveChatMessage(chatRoom, ChatMessage.Sender.AI, "죄송합니다. AI 서버 연결에 실패했습니다.", ChatMessage.MessageType.TALK, null);
            AiResponse errorResponse = new AiResponse();
            errorResponse.setReply("죄송합니다. AI 서버 연결에 실패했습니다.");
            return errorResponse;
        }

        if (aiResponse == null) {
            AiResponse errorResponse = new AiResponse();
            errorResponse.setReply("응답을 생성할 수 없습니다.");
            return errorResponse;
        }

        if (aiResponse.getIntent() != null && ("build".equals(aiResponse.getIntent()) || "modify".equals(aiResponse.getIntent()))) {
            handleEstimateResponse(chatRoom, userId, aiResponse);
        } else {
            String reply = aiResponse.getReply();
            if (reply == null || reply.isEmpty()) {
                reply = "응답을 생성할 수 없습니다.";
                aiResponse.setReply(reply);
            }
            saveChatMessage(chatRoom, ChatMessage.Sender.AI, reply, ChatMessage.MessageType.TALK, null);
        }

        return aiResponse;
    }

    /**
     * AI 견적 응답 처리 (DB 저장 및 메시지 연결)
     */
    private void handleEstimateResponse(ChatRoom chatRoom, String userId, AiResponse response) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        long parseTotal = 0;
        try {
            if (response.getTotal() != null) {
                String rawTotal = response.getTotal().replaceAll("[^0-9]", ""); // 숫자만 추출
                if (!rawTotal.isEmpty()) {
                    parseTotal = Long.parseLong(rawTotal);
                }
            }
        } catch (Exception e) {
            log.warn("Price parsing failed: {}", response.getTotal());
        }

        AiEstimate estimate = AiEstimate.builder()
            .user(user)
            .chatRoom(chatRoom)
            .intent(response.getIntent())
            .intro(response.getIntro())
            .note(response.getNote())
            .totalPrice(parseTotal)
            .createdAt(LocalDateTime.now()) // Auditing을 쓴다면 생략 가능
            .build();

        if (response.getMain() != null) {
            List<AiEstimateProduct> products = new ArrayList<>();

            for (Map.Entry<String, AiComponent> entry : response.getMain().entrySet()) {
                String categoryKey = entry.getKey();
                AiComponent comp = entry.getValue();

                if (comp == null) continue;

                AiEstimateProduct product = AiEstimateProduct.builder()
                    .aiEstimate(estimate)
                    .category(categoryKey) // cpu, gpu ...
                    .name(comp.getName())
                    .price(comp.getPrice())
                    .description(comp.getDescription())
                    .build();

                products.add(product);
            }
            estimate.setProducts(products);
        }

        AiEstimate savedEstimate = aiEstimateRepository.save(estimate);

        String messageContent = response.getIntro() + "\n\n" + response.getNote();
        if (response.getAnotherInputText() != null) {
            messageContent += "\n\n" + response.getAnotherInputText();
        }

        saveChatMessage(
            chatRoom,
            ChatMessage.Sender.AI,
            messageContent,
            ChatMessage.MessageType.ESTIMATE,
            savedEstimate.getId()
        );

        log.info("Saved AiEstimate ID: {}", savedEstimate.getId());
    }

    /**
     * 채팅 메시지 저장 헬퍼 메서드
     */
    private void saveChatMessage(ChatRoom chatRoom, ChatMessage.Sender sender, String content, ChatMessage.MessageType type, Long estimateId) {
        ChatMessage message = ChatMessage.builder()
            .chatRoom(chatRoom)
            .sender(sender)
            .content(content)
            .type(type)
            .relatedEstimateId(estimateId)
            .build();

        chatMessageRepository.save(message);
    }

    /**
     * 채팅방 생성 (첫 진입 시)
     */
    @Transactional
    public ChatRoom createChatRoom(String userId) {
        User user = userRepository.findById(userId).orElseThrow();
        ChatRoom chatRoom = ChatRoom.builder()
            .user(user)
            .title("새로운 견적 상담")
            .build();
        return chatRoomRepository.save(chatRoom);
    }

    @Transactional(readOnly = true)
    public List<ChatRoom> getUserChatRooms(String userId) {
        return chatRoomRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }
}