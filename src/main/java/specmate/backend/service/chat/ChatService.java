package specmate.backend.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import specmate.backend.dto.aiestimate.EstimateResult;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.chatroom.ChatRoomRequest;
import specmate.backend.dto.chatroom.ChatRoomResponse;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.MessageStatus;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.chat.*;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiEstimateRepository aiEstimateRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final ProductRepository productRepository;
    private final EstimateResultProcessor estimateResultProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient();

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.assistant-id}")
    private String assistantId;

    /** 사용자 메시지 처리 */
    @Transactional
    public ChatMessage processUserMessage(String roomId, String content) throws IOException {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        ChatMessage userMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.USER)
                .content(content)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMsg);

        // OpenAI Thread에 메시지 추가
        addMessageToThread(room.getThread(), content);

        // Run 생성 및 완료 대기
        String runId = createRun(room.getThread());
        String assistantReply = waitForRunCompletion(room.getThread(), runId);

        // AI 응답 메시지 저장
        ChatMessage assistantMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(assistantReply)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMsg);

        // 응답 JSON 파싱
        EstimateResult result = estimateResultProcessor.parse(assistantReply);

        // AI 견적 저장
        AiEstimate estimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .message(assistantMsg)
                .title(result.getBuildName())
                .totalPrice(parsePrice(result.getTotalPrice()))
                .status("SUCCESS")
                .build();

        LocalDateTime now = LocalDateTime.now();
        estimate.setCreatedAt(now);
        estimate.setUpdatedAt(now);

        aiEstimateRepository.save(estimate);

        // 부품 저장    
        if (result.getProducts() != null && !result.getProducts().isEmpty()) {
            result.getProducts().forEach(p -> {
                Product matchedProduct = productRepository
                        .findByNameContainingIgnoreCase(p.getName())
                        .stream()
                        .filter(prod -> prod.getType().equalsIgnoreCase(p.getType()))
                        .findFirst()
                        .orElse(null);

                if (matchedProduct != null) {
                    EstimateProduct ep = EstimateProduct.builder()
                            .aiEstimate(estimate)
                            .product(matchedProduct)
                            .aiName(p.getName())
                            .matched(true)
                            .similarity(p.getSimilarity() != null ? p.getSimilarity().floatValue() : 0.0f)
                            .quantity(1)
                            .unitPrice(parsePrice(p.getPrice()))
                            .totalPrice(parsePrice(p.getPrice()))
                            .createdAt(LocalDateTime.now())
                            .build();

                    estimateProductRepository.save(ep);
                }
            });
        }

        return assistantMsg;
    }

    // Thread 생성
    private String createThread() throws IOException {
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/threads")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(RequestBody.create("{}", JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.get("id").asText();
        }
    }

    // 메시지 추가
    private void addMessageToThread(String threadId, String content) throws IOException {
        String bodyJson = objectMapper.writeValueAsString(
                Map.of("role", "user", "content", content)
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/messages")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        client.newCall(request).execute().close();
    }

    // Run 생성
    private String createRun(String threadId) throws IOException {
        String bodyJson = "{ \"assistant_id\": \"" + assistantId + "\" }";

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/runs")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Run 생성 실패: " + response.body().string());
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.get("id").asText();
        }
    }

    // Run 완료 대기
    private String waitForRunCompletion(String threadId, String runId) throws IOException {
        String status = "in_progress";
        while (!status.equals("completed")) {
            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("OpenAI-Beta", "assistants=v2")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                JsonNode root = objectMapper.readTree(response.body().string());
                status = root.get("status").asText();
                if (status.equals("completed")) break;
            }

            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        Request msgRequest = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/messages")
                .header("Authorization", "Bearer " + apiKey)
                .header("OpenAI-Beta", "assistants=v2")
                .build();

        try (Response response = client.newCall(msgRequest).execute()) {
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.get("data").get(0).get("content").get(0).get("text").get("value").asText();
        }
    }

    @Transactional
    public ChatRoomResponse createChatRoom(String userId, String title) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));

        String threadId = createThread();

        ChatRoom room = ChatRoom.builder()
                .user(user)
                .title(title != null ? title : "새 채팅방")
                .thread(threadId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        chatRoomRepository.save(room);
        return ChatRoomResponse.fromEntity(room);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatMessages(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));
        return chatMessageRepository.findByChatRoom(room).stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getUserChatRooms(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));

        return chatRoomRepository.findByUser(user).stream()
                .map(ChatRoomResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse getChatRoom(String roomId) {
        return chatRoomRepository.findById(roomId)
                .map(ChatRoomResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));
    }


    @Transactional
    public ChatRoomResponse updateChatRoom(String roomId, ChatRoomRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        room.setTitle(request.getTitle());
        room.setUpdatedAt(LocalDateTime.now());

        chatRoomRepository.save(room);
        return ChatRoomResponse.fromEntity(room);
    }

    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        chatMessageRepository.deleteAllByChatRoom(room);
        aiEstimateRepository.deleteAllByChatRoom(room);

        chatRoomRepository.delete(room);
    }

    private Integer parsePrice(String priceStr) {
        if (priceStr == null) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
