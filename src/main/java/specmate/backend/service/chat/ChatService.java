package specmate.backend.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import specmate.backend.config.JwtTokenProvider;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.MessageStatus;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.repository.chat.*;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${OPENAI_API_KEY}")
    private String API_KEY;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** REST API용 - userId 직접 받음 */
    public GPTResponse processUserPrompt(String userId, String prompt) throws IOException {
        List<Product> products = productRepository.findAll();
        return callGptApi(prompt, products);
    }

    /** WebSocket용 - token 받아서 userId 추출 */
    public GPTResponse processUserPromptWithToken(String token, String prompt) throws IOException {
        String userId = jwtTokenProvider.getUserId(token);
        return processUserPrompt(userId, prompt);
    }

    /** OpenAI API 호출 */
    public GPTResponse callGptApi(String prompt, List<Product> products) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .callTimeout(200, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();

        // DB에서 조회된 부품들을 문자열로 변환
        StringBuilder productList = new StringBuilder();
        for (Product p : products) {
            String price = "가격 정보 없음";
            if (p.getLowestPrice() != null && p.getLowestPrice().get("price") != null) {
                price = p.getLowestPrice().get("price").toString() + "원";
            }
            productList.append("- ")
                    .append(p.getType()).append(": ")
                    .append(p.getName()).append(" (")
                    .append(price).append(")\n");
        }

        String finalPrompt =
                "당신은 \"PC 견적 구성 전문가\"입니다.\n" +
                        "아래는 데이터베이스에서 검색된 부품 후보입니다.\n" +
                        "이 부품들을 참고하여 사용자의 요구에 맞는 최적의 PC 견적을 JSON으로 작성하세요.\n\n" +
                        "규칙:\n" +
                        "- name과 price는 반드시 아래 검색 결과에서 제공된 값을 그대로 사용하세요.\n" +
                        "- description은 사용자의 요구(화이트 감성, 게이밍, 영상편집 등)를 반영하여 새롭게 작성하세요.\n" +
                        "- total은 모든 price를 합산해 \"1,830,000원\"처럼 문자열로 표시하세요.\n" +
                        "- another_input_text에는 사용자가 추가로 물어볼 수 있는 질문을 3~5개 생성하세요.\n" +
                        "- JSON 이외의 텍스트는 절대 출력하지 마세요.\n\n" +
                        "[검색된 부품 데이터]\n" + productList + "\n\n" +
                        "사용자 입력: " + prompt;

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("model", "gpt-5-mini-2025-08-07");
        jsonMap.put("messages", List.of(Map.of("role", "user", "content", finalPrompt)));

        String json = objectMapper.writeValueAsString(jsonMap);

        RequestBody body = RequestBody.create(
                json, MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("GPT API 호출 실패: {}", response);
                throw new IOException("GPT API 호출 실패: " + response);
            }
            String resbody = response.body().string();
            log.debug("GPT Response Raw: {}", resbody);
            return objectMapper.readValue(resbody, GPTResponse.class);
        }
    }

    /** 유저 메시지 저장 */
    public ChatMessage saveUserMessage(ChatRoom room, String content) {
        ChatMessage userMessage = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.USER)
                .content(content)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
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
                .build();
        return chatMessageRepository.save(assistantMessage);
    }

    /** 유저의 채팅방 목록 조회 */
    public List<ChatRoom> getUserChatRooms(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 유저입니다."));
        return chatRoomRepository.findAllByUser(user);
    }

    /** 채팅방 메시지 조회 */
    public List<ChatMessage> getChatMessages(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
        return chatMessageRepository.findAllByChatRoom(room);
    }

    /** 채팅방 삭제 */
    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
        chatRoomRepository.delete(room);
    }

    public ChatRoom getChatRoomById(String roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
    }
}
