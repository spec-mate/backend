package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.aiestimate.EstimateResult;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.chatroom.ChatRoomRequest;
import specmate.backend.dto.chatroom.ChatRoomResponse;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.MessageStatus;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.chat.*;
import specmate.backend.repository.user.UserRepository;
import specmate.backend.service.product.ProductSearchService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiEstimateRepository aiEstimateRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final UserRepository userRepository;
    private final EstimateResultProcessor estimateResultProcessor;
    private final ProductSearchService productSearchService;
    private final ChatModel chatModel;

    /** 사용자 입력 → 카테고리별 RAG 검색 + GPT 견적 생성 */
    @Transactional
    public ChatMessage processUserMessage(String roomId, String content) {

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        // 사용자 메시지 저장
        ChatMessage userMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.USER)
                .content(content)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMsg);

        /** RAG: 9개 카테고리별 유사도 검색 */
        List<Product> retrievedProducts = productSearchService.searchSimilarProductsByCategory(content, 5);
        if (retrievedProducts.isEmpty()) {
            log.warn("RAG 검색 결과 없음 - {}", content);
        }

        /** DB type → GPT 표준 type 매핑 테이블 */
        Map<String, String> typeMap = Map.ofEntries(
                Map.entry("cpu", "cpu"),
                Map.entry("vga", "gpu"),
                Map.entry("ram", "ram"),
                Map.entry("RAM", "ram"),
                Map.entry("ssd", "storage"),
                Map.entry("power", "psu"),
                Map.entry("mainboard", "motherboard"),
                Map.entry("cooler", "cooler"),
                Map.entry("hdd", "hdd"),
                Map.entry("case", "case")
        );

        /** GPT 입력용 블록 텍스트 구성 */
        String componentText = retrievedProducts.stream()
                .collect(Collectors.groupingBy(p -> {
                    String rawType = p.getType() != null ? p.getType().toLowerCase() : "unknown";
                    return typeMap.getOrDefault(rawType, rawType);
                }))
                .entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey)) // 일정 순서 유지
                .map(entry -> {
                    String type = entry.getKey();
                    String title = switch (type) {
                        case "cpu" -> "[CPU 목록]";
                        case "gpu" -> "[GPU 목록]";
                        case "ram" -> "[RAM 목록]";
                        case "storage" -> "[STORAGE 목록]";
                        case "psu" -> "[PSU 목록]";
                        case "motherboard" -> "[MOTHERBOARD 목록]";
                        case "cooler" -> "[COOLER 목록]";
                        case "hdd" -> "[HDD 목록]";
                        case "case" -> "[CASE 목록]";
                        default -> "[기타 목록]";
                    };

                    String block = entry.getValue().stream()
                            .limit(10)
                            .map(p -> {
                                String price = (p.getLowestPrice() != null && p.getLowestPrice().get("price") != null)
                                        ? p.getLowestPrice().get("price").toString() + "원"
                                        : "가격 정보 없음";
                                String specs = p.getOptions() != null
                                        ? p.getOptions().entrySet().stream()
                                        .limit(5)
                                        .map(e -> e.getKey() + ": " + e.getValue())
                                        .collect(Collectors.joining(", "))
                                        : "옵션 없음";
                                return String.format("제품명: %s\n제조사: %s\n분류(type): %s\n가격: %s\n주요 옵션: %s",
                                        p.getName(), p.getManufacturer(), p.getType(), price, specs);
                            })
                            .collect(Collectors.joining("\n---\n"));
                    return title + "\n" + block;
                })
                .collect(Collectors.joining("\n\n"));

        /** 외부 프롬프트 파일 로드 */
        String rolePrompt = readPromptFile("prompts/assistant_role.txt");
        String rulePrompt = readPromptFile("prompts/output_rules.txt");

        /** 최종 시스템 프롬프트 구성 */
        String systemPrompt = rolePrompt + "\n" + rulePrompt +
                "\n\n[검색된 부품 후보 데이터]\n" + componentText +
                "\n\n[사용자 요청]\n" + content;

        /** GPT 호출 */
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(content)
        ));
        var response = chatModel.call(prompt);
        String reply = response.getResult().getOutput().getText();

        /** GPT 응답 저장 */
        ChatMessage assistantMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(reply)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMsg);

        /** JSON 파싱 및 견적 저장 */
        EstimateResult result = estimateResultProcessor.parse(reply);

        AiEstimate estimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .message(assistantMsg)
                .title(result.getBuildName())
                .totalPrice(parsePrice(result.getTotalPrice()))
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        aiEstimateRepository.save(estimate);

        return assistantMsg;
    }

    /** ClassPath 파일 로드 */
    private String readPromptFile(String path) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("프롬프트 파일 로드 실패: " + path, e);
        }
    }

    @Transactional
    public ChatRoomResponse createChatRoom(String userId, String title) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));
        ChatRoom room = ChatRoom.builder()
                .user(user)
                .title(title != null ? title : "새 채팅방")
                .thread("N/A")
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
        return chatMessageRepository.findByChatRoomOrderByCreatedAtAsc(room)
                .stream().map(ChatMessageResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getUserChatRooms(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));
        return chatRoomRepository.findByUserOrderByCreatedAtDesc(user)
                .stream().map(ChatRoomResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
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
