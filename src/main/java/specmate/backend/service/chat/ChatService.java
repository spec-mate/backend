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
    private final AiEstimateRepository aiEstimateRepository;
    private final ProductRepository productRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private final AssistantRepository assistantRepository;

    /** REST API용 - userId 직접 받음 */
    public GPTResponse processUserPrompt(String userId, String prompt) throws IOException {
        List<Product> products = productRepository.findAll();
        GPTResponse gptResponse = callGptApi(prompt, products);
        saveChatAndEstimate(userId, prompt, gptResponse);
        return gptResponse;
    }

    /** WebSocket용 - token 받아서 userId 추출 */
    public GPTResponse processUserPromptWithToken(String token, String prompt) throws IOException {
        String userId = jwtTokenProvider.getUserId(token);
        return processUserPrompt(userId, prompt);
    }

    /** OpenAI API 호출 */
    public GPTResponse callGptApi(String prompt, List<Product> products) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)  // 연결 시도 제한
                .writeTimeout(30, TimeUnit.SECONDS)    // 요청 본문 전송 제한
                .readTimeout(120, TimeUnit.SECONDS)    // 응답 대기 시간 충분히 확보
                .build();

        // DB에서 조회된 부품들을 문자열로 변환
        StringBuilder productList = new StringBuilder();
        for (Product p : products) {
            // 최저가 정보 추출
            String price = "가격 정보 없음";
            if (p.getLowestPrice() != null && p.getLowestPrice().get("price") != null) {
                price = p.getLowestPrice().get("price").toString() + "원";
            }

            // 부품 카테고리는 type 사용
            productList.append("- ")
                    .append(p.getType()).append(": ")
                    .append(p.getName()).append(" (")
                    .append(price).append(")\n");
        }

        // 최종 프롬프트 구성
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
                        "[검색된 부품 데이터]\n" + productList.toString() + "\n\n" +
                        "사용자 입력: " + prompt;

        // OpenAI API 요청 JSON 구성
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("model", "gpt-5-mini-2025-08-07");
        jsonMap.put("messages", List.of(
                Map.of("role", "user", "content", finalPrompt)
        ));

        String json = objectMapper.writeValueAsString(jsonMap);

        RequestBody body = RequestBody.create(
                json, MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        try(Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful()) {
                log.error("GPT API 호출 실패: {}", response);
                throw new IOException("GPT API 호출 실패: " + response);
            }

            String resbody = response.body().string();
            log.debug("GPT Response Raw: {}", resbody);

            return objectMapper.readValue(resbody, GPTResponse.class);
        }
    }


    /** 채팅/AI 추천 저장 */
    private void saveChatAndEstimate(String userId, String userPrompt, GPTResponse gptResponse) {
        // 1) User 엔터티 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 유저입니다."));

        // 2) 채팅방 가져오기 or 생성
        ChatRoom room = chatRoomRepository.findByUser(user)
                .orElseGet(() -> {
            ChatRoom newRoom = new ChatRoom();
            newRoom.setUser(user);
            newRoom.setTitle("새 채팅방");
            newRoom.setCreatedAt(LocalDateTime.now());
            return chatRoomRepository.save(newRoom);
        });

        // 3) GPT 응답 텍스트
        String gptResponseText = gptResponse.getMessage();
        room.setLastMessage(gptResponseText);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        // 4) 유저 메시지 저장
        ChatMessage userMessage = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.USER)
                .content(userPrompt)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMessage);

        // 5) GPT 메시지 저장
        ChatMessage assistantMessage = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(gptResponseText)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMessage);

        // 6) AiEstimate 생성 (Assistant 조회 + fallback 생성)
        Assistant defaultAssistant = assistantRepository.findByName("default")
                .orElseGet(() -> {
                    Assistant newAssistant = Assistant.builder()
                            .name("default")
                            .description("자동 생성된 기본 어시스턴트")
                            .instruction("기본 프롬프트")
                            .model("gpt-5-mini-2025-08-07")
                            .isActive(true)
                            .build();
                    return assistantRepository.save(newAssistant);
                });

        AiEstimate aiEstimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .assistant(defaultAssistant)
                .message(assistantMessage)
                .title("GPT 추천 견적")
                .status("success")
                .totalPrice(0)
                .build();

        aiEstimateRepository.save(aiEstimate);
        // 7) GPT 응답에서 상품명 추출 & 전처리
        String normalizedName = normalizeProductName(gptResponseText);
        if (normalizedName == null || normalizedName.trim().isEmpty()) {
            normalizedName = "없음";
        }

        // 8) DB 탐색
        List<Product> candidates = productRepository.findByNameContainingIgnoreCase(normalizedName);

        Product product = null;
        if (!candidates.isEmpty()) {
            product = candidates.get(0); // 첫 번째 결과만 사용
        }

        if (product != null) {
            // 9) EstimateProduct 생성
            Integer unitPrice = extractLowestPrice(product);
            EstimateProduct ep = EstimateProduct.builder()
                    .aiEstimate(aiEstimate)
                    .product(product)
                    .aiName(normalizedName.isEmpty() ? "알수없음" : normalizedName)
                    .matched(true)
                    .similarity(1.0f)
                    .quantity(1)
                    .unitPrice(unitPrice != null ? unitPrice : 0)
                    .totalPrice(unitPrice != null ? unitPrice : 0)
                    .createdAt(LocalDateTime.now())
                    .build();
            estimateProductRepository.save(ep);

            // 10) AiEstimate 총액 업데이트
            aiEstimate.setTotalPrice(unitPrice);
            aiEstimateRepository.save(aiEstimate);
        }
    }

    /** GPT 결과 전처리 */
    private String normalizeProductName(String text) {
        return text.replaceAll("[^a-zA-Z0-9가-힣 ]", "")
                .trim()
                .toLowerCase();
    }

    /** Product에서 최저가 추출 */
    private Integer extractLowestPrice(Product product) {
        if (product.getLowestPrice() != null) {
            Object priceObj = product.getLowestPrice().get("price");
            if (priceObj != null) {
                String priceStr = priceObj.toString().replaceAll("[^0-9]", "");
                if (!priceStr.isEmpty()) {
                    return Integer.parseInt(priceStr);
                }
            }
        }
        return 0;
    }



    /** 유저의 채팅방 목록 조회 */
    public List<ChatRoom> getUserChatRooms(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 유저입니다."));

        return chatRoomRepository.findAllByUser(user);
    }

    /** */
    public List<ChatMessage> getChatMessages(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
        return chatMessageRepository.findAllByChatRoom(room);
    }

    /** */
    public List<AiEstimate> getEstimates(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
        return aiEstimateRepository.findAllByChatRoom(room);
    }
    /** */
    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
        chatRoomRepository.delete(room);
    }
}
