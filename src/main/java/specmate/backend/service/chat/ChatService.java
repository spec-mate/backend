package specmate.backend.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
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
import specmate.backend.service.assistant.AssistantService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiEstimateRepository aiEstimateRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final ProductRepository productRepository;
    private final AssistantService assistantService;
    private final EstimateResultProcessor estimateResultProcessor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

//    /** 새로운 채팅방 생성 */
//    @Transactional
//    public ChatRoom createChatRoom(String userId) throws IOException {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));
//
//        Assistant assistant = assistantService.getActiveAssistant();
//
//        // OpenAI Thread 생성
//        String threadId = createThread();
//
//        ChatRoom room = ChatRoom.builder()
//                .user(user)
//                .assistant(assistant)
//                .title("새 채팅방")
//                .thread(threadId)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .build();
//
//        return chatRoomRepository.save(room);
//    }

    /** 사용자 메시지 전송 */
    @Transactional
    public ChatMessage processUserMessage(String roomId, String content) throws IOException {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        // 1) 사용자 메시지 저장
        ChatMessage userMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.USER)
                .content(content)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMsg);

        // 2) OpenAI Thread에 메시지 추가
        addMessageToThread(room.getThread(), content);

        // 3) Run 실행
        Assistant assistant = room.getAssistant();
        String runId = createRun(room.getThread(), assistant.getId());

        // 4) Run 완료 후 응답 가져오기
        String assistantReply = waitForRunCompletion(room.getThread(), runId);

        // 5) 어시스턴트 메시지 저장
        ChatMessage assistantMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(assistantReply)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMsg);

        // 6) GPT 응답 → EstimateResult 파싱
        EstimateResult result = estimateResultProcessor.parse(assistantReply);

        // 7) AiEstimate 저장
        AiEstimate estimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .assistant(assistant)
                .message(assistantMsg)
                .title(result.getBuildName())
                .totalPrice(parsePrice(result.getTotalPrice()))
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        aiEstimateRepository.save(estimate);

        // 8) EstimateProduct 저장
        if (result.getProducts() != null) {
            result.getProducts().forEach(p -> {
                Product matchedProduct = productRepository
                        .findByNameContainingIgnoreCase(p.getName())
                        .stream()
                        .filter(prod -> prod.getType().equalsIgnoreCase(p.getType()))
                        .findFirst()
                        .orElse(null);

                EstimateProduct ep = EstimateProduct.builder()
                        .aiEstimate(estimate)
                        .product(matchedProduct)
                        .aiName(p.getName())
                        .matched(matchedProduct != null)
                        .similarity(p.getSimilarity() != null ? p.getSimilarity().floatValue() : 0.0f)
                        .quantity(1)
                        .unitPrice(parsePrice(p.getPrice()))
                        .totalPrice(parsePrice(p.getPrice()))
                        .createdAt(LocalDateTime.now())
                        .build();

                estimateProductRepository.save(ep);
            });
        }

        return assistantMsg;
    }

    private String createThread() throws IOException {
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/threads")
                .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(RequestBody.create("{}", JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.get("id").asText();
        }
    }

    private void addMessageToThread(String threadId, String content) throws IOException {
        String bodyJson = objectMapper.writeValueAsString(
                new MessageDTO("user", content)
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/messages")
                .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        client.newCall(request).execute().close();
    }

    private String createRun(String threadId, String assistantId) throws IOException {
        String bodyJson = "{ \"assistant_id\": \"" + assistantId + "\" }";

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/runs")
                .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            if (!response.isSuccessful()) {
                log.error("Run 생성 실패: {}", responseBody);
                throw new IOException("Run 생성 실패: " + responseBody);
            }

            if (root.get("id") == null) {
                log.error("Run 응답에 'id' 없음: {}", responseBody);
                throw new IOException("Run 응답에 'id' 없음: " + responseBody);
            }

            return root.get("id").asText();
        }
    }

    private String waitForRunCompletion(String threadId, String runId) throws IOException {
        String status = "in_progress";
        while (!status.equals("completed")) {
            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId)
                    .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                    .header("OpenAI-Beta", "assistants=v2")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                JsonNode root = objectMapper.readTree(response.body().string());
                status = root.get("status").asText();
                if (status.equals("completed")) break;
            }

            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // 결과 메시지 조회
        Request msgRequest = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/messages")
                .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .header("OpenAI-Beta", "assistants=v2")
                .build();

        try (Response response = client.newCall(msgRequest).execute()) {
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.get("data").get(0).get("content").get(0).get("text").get("value").asText();
        }
    }

    @Transactional
    public ChatRoomResponse createChatRoomWithAssistant(String title) throws IOException {
        Assistant assistant = assistantService.createAssistant(
                "PC 견적 어시스턴트",
                "사용자 요청에 맞는 PC 견적을 추천하는 전문가",
                "당신은 컴퓨터 부품 전문가이며, 사용자의 예산과 용도에 맞게 최적의 견적을 제공합니다.",
                "gpt-4o-mini"
        );

        // 2) OpenAI Thread 생성
        String threadId = createThread();

        // 3) ChatRoom 저장
        ChatRoom room = ChatRoom.builder()
                .title(title != null ? title : "채팅방")
                .assistant(assistant)
                .thread(threadId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        chatRoomRepository.save(room);

        return ChatRoomResponse.fromEntity(room);
    }


    @Transactional
    public List<ChatMessageResponse> getChatMessages(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        return chatMessageRepository.findAllByChatRoom(room).stream()
                .map(msg -> ChatMessageResponse.builder()
                        .roomId(room.getId())
                        .sender(msg.getSender().name()) // USER, ASSISTANT
                        .content(msg.getContent())
                        .parsedJson(null)
                        .createdAt(msg.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public List<ChatRoomResponse> getUserChatRooms(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));

        return chatRoomRepository.findAllByUser(user).stream()
                .map(ChatRoomResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ChatRoomResponse getChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        return ChatRoomResponse.fromEntity(room);
    }

    @Transactional
    public ChatRoomResponse updateChatRoom(String roomId, ChatRoomRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        room.setTitle(request.getTitle());
        room.setUpdatedAt(LocalDateTime.now());

        ChatRoom updatedRoom = chatRoomRepository.save(room);
        return ChatRoomResponse.fromEntity(updatedRoom);
    }

    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        // 메시지 먼저 삭제
        chatMessageRepository.deleteAllByChatRoom(room);

        // AiEstimate와 EstimateProduct도 같이 삭제
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

    record MessageDTO(String role, String text) {}
}
