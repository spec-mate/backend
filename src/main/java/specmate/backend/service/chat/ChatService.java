package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import specmate.backend.dto.aiestimate.EstimateResult;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.chatroom.ChatRoomRequest;
import specmate.backend.dto.chatroom.ChatRoomResponse;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.MessageStatus;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.repository.chat.ChatMessageRepository;
import specmate.backend.repository.chat.ChatRoomRepository;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;
import specmate.backend.service.product.ProductSearchService;

import java.time.Duration;
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
    private final UserRepository userRepository;
    private final EstimateResultProcessor estimateResultProcessor;
    private final ProductSearchService productSearchService;
    private final RestClient openAiRestClient;
    private final ProductRepository productRepository;

    @Value("${openai.assistant.id:${OPENAI_ASSISTANT_ID:${openai_assistant_id:}}}")
    private String assistantId;

    private static final int PER_TYPE_LIMIT = 10;
    private static final int SEARCH_LIMIT_PER_CATEGORY = 5;
    private static final int MAX_INSTRUCTIONS_CHARS = 12_000;
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(60);

    private static final Map<String, String> TITLE_BY_STD_TYPE = Map.ofEntries(
            Map.entry("cpu", "[CPU 목록]"),
            Map.entry("ssd", "[SSD 목록]"),
            Map.entry("RAM", "[RAM 목록]"),
            Map.entry("vga", "[VGA 목록]"),
            Map.entry("power", "[PSU 목록]"),
            Map.entry("mainboard", "[MAINBOARD 목록]"),
            Map.entry("cooler", "[COOLER 목록]"),
            Map.entry("hdd", "[HDD 목록]"),
            Map.entry("case", "[CASE 목록]")
    );


    private String createThread() {
        var res = openAiRestClient.post().uri("/threads")
                .body(Map.of()).retrieve().body(ThreadRes.class);
        if (res == null || res.id == null)
            throw new IllegalStateException("Thread 생성 실패");
        return res.id;
    }

    private void addUserMessage(String threadId, String text) {
        openAiRestClient.post()
                .uri("/threads/{id}/messages", threadId)
                .body(Map.of("role", "user", "content", text))
                .retrieve().toBodilessEntity();
    }

    private String runAssistant(String threadId, String instructions) {
        var body = new HashMap<String,Object>();
        body.put("assistant_id", assistantId);

        if (instructions != null && !instructions.isBlank()) {
            body.put("additional_instructions", instructions);
        }


        var res = openAiRestClient.post()
                .uri("/threads/{id}/runs", threadId)
                .body(body)
                .retrieve()
                .body(RunRes.class);

        if (res == null || res.id == null)
            throw new IllegalStateException("Run 생성 실패");

        return res.id;
    }


    private void waitUntilCompleted(String threadId, String runId, Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        long sleep = 600;
        while (System.currentTimeMillis() < end) {
            var run = openAiRestClient.get().uri("/threads/{t}/runs/{r}", threadId, runId)
                    .retrieve().body(RunRes.class);
            String status = run != null ? run.status : null;
            if ("completed".equalsIgnoreCase(status)) return;
            if (status != null && Set.of("failed","cancelled","expired").contains(status))
                throw new IllegalStateException("Run 상태: " + status);
            try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            if (sleep < 2000) sleep += 200;
        }
        throw new IllegalStateException("Run 대기 시간 초과");
    }

    private String fetchLatestAssistantText(String threadId) {
        var res = openAiRestClient.get()
                .uri(uri -> uri.path("/threads/{id}/messages")
                        .queryParam("limit", "20").build(threadId))
                .retrieve().body(MessagesRes.class);
        if (res == null || res.data == null) return null;
        return res.data.stream()
                .filter(m -> "assistant".equalsIgnoreCase(m.role))
                .map(this::extractText).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String extractText(Msg m) {
        if (m.content == null) return null;
        return m.content.stream()
                .filter(c -> "text".equalsIgnoreCase(c.type))
                .map(c -> c.text != null ? c.text.value : null)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    private record ThreadRes(String id) {}
    private record RunRes(String id, String status) {}
    private record MessagesRes(List<Msg> data) {}
    private record Msg(String id, String role, List<C> content) {}
    private record C(String type, T text) {}
    private record T(String value) {}


    @Transactional
    public ChatMessage processUserMessage(String roomId, String content) {

        if (assistantId == null || assistantId.isBlank()) {
            throw new IllegalStateException("OPENAI_ASSISTANT_ID가 설정되지 않았습니다.");
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        ensureThread(room); // ChatRoom 단위 thread 보장

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

        // RAG 검색
        List<Product> retrieved = productSearchService.searchSimilarProductsByCategory(content, SEARCH_LIMIT_PER_CATEGORY);
        if (retrieved.isEmpty()) log.warn("RAG 검색 결과 없음 - {}", content);

        String componentText = buildComponentText(retrieved);
        if (componentText.length() > MAX_INSTRUCTIONS_CHARS) {
            componentText = componentText.substring(0, MAX_INSTRUCTIONS_CHARS) + "\n...\n";
        }

        // 1. fallbackMap 생성 (retrieved 리스트에서 type별 대표 제품 추출)
        Map<String, specmate.backend.entity.Product> fallbackMap = retrieved.stream()
                .filter(p -> p.getType() != null)
                .collect(Collectors.toMap(
                        p -> normalizeStdType(p.getType()), // 표준화된 type 키
                        p -> p,
                        (a, b) -> a // 중복 시 첫 번째 유지
                ));

        // 2. entity → DTO 변환 (EstimateResult.Product)
        Map<String, specmate.backend.dto.aiestimate.EstimateResult.Product> dtoFallbackMap =
                fallbackMap.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            specmate.backend.entity.Product src = e.getValue();
                            specmate.backend.dto.aiestimate.EstimateResult.Product dto =
                                    new specmate.backend.dto.aiestimate.EstimateResult.Product();
                            dto.setType(src.getType());
                            dto.setName(src.getName());

                            // description 대체 (options 기반)
                            dto.setDescription(
                                    src.getOptions() != null && !src.getOptions().isEmpty()
                                            ? src.getOptions().entrySet().stream()
                                            .limit(3)
                                            .map(opt -> opt.getKey() + ": " + opt.getValue())
                                            .collect(Collectors.joining(", "))
                                            : "정보 없음"
                            );

                            // 가격 추출
                            dto.setPrice(src.getLowestPrice() != null
                                    ? src.getLowestPrice().getOrDefault("price", "0").toString()
                                    : "0");
                            return dto;
                        }
                ));


        // 3. Assistant instructions (RAG + 사용자 요청)
        String instructions = (componentText.isBlank() ? "" : "[검색된 부품 후보 데이터]\n" + componentText)
                + "\n\n[사용자 요청]\n" + content;

        // 4. Assistant 실행
        String reply;
        try {
            log.info("componentText 길이 = {}", componentText == null ? 0 : componentText.length());
            if (componentText == null || componentText.isBlank()) {
                log.warn("componentText가 비어 있습니다. RAG 데이터가 Assistant로 전달되지 않습니다.");
            } else {
                log.debug("componentText 일부 미리보기:\n{}", componentText.substring(0, Math.min(500, componentText.length())));
            }

            addUserMessage(room.getThread(), content);
            String runId = runAssistant(room.getThread(), instructions.isBlank() ? null : instructions);
            waitUntilCompleted(room.getThread(), runId, RUN_TIMEOUT);

            reply = Optional.ofNullable(fetchLatestAssistantText(room.getThread()))
                    .map(this::extractJsonOrRaw)
                    .orElse("응답 생성 실패");

        } catch (Exception e) {
            log.error("Assistants 호출 실패 roomId={}, threadId={}", roomId, room.getThread(), e);
            reply = "죄송합니다. 일시적인 오류로 응답을 생성하지 못했습니다.";
        }

        // 5. GPT 응답 메시지 DB 저장
        ChatMessage assistantMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(reply)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMsg);

        // 6. 견적 파싱 (GPT JSON + fallbackMap 결합)
        EstimateResult result = estimateResultProcessor.parse(reply, dtoFallbackMap);

        // 7. 견적 결과 저장
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


    /** ChatRoom에 thread 없으면 새로 생성 */
    private void ensureThread(ChatRoom room) {
        if (room.getThread() == null || room.getThread().isBlank() || "N/A".equalsIgnoreCase(room.getThread())) {
            String newThread = createThread();
            room.setThread(newThread);
            room.setUpdatedAt(LocalDateTime.now());
            chatRoomRepository.save(room);
            log.info("thread created for room {}: {}", room.getId(), newThread);
        }
    }

    /** RAG 블록 문자열 구성 */
    private String buildComponentText(List<Product> products) {
        if (products == null || products.isEmpty()) return "";

        // 표준 타입으로 그룹핑
        Map<String, List<Product>> byStdType = products.stream()
                .collect(Collectors.groupingBy(p -> normalizeStdType(
                        Optional.ofNullable(p.getType()).orElse("unknown"))));

        // 타입 알파벳 정렬로 출력
        return byStdType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String stdType = entry.getKey();
                    String title = TITLE_BY_STD_TYPE.getOrDefault(stdType, "[기타 목록]");

                    String block = entry.getValue().stream()
                            .limit(PER_TYPE_LIMIT)
                            .map(p -> {
                                String price = (p.getLowestPrice() != null && p.getLowestPrice().get("price") != null)
                                        ? p.getLowestPrice().get("price").toString() + "원"
                                        : "가격 정보 없음";

                                String specs = (p.getOptions() != null && !p.getOptions().isEmpty())
                                        ? p.getOptions().entrySet().stream()
                                        .limit(5)
                                        .map(e -> e.getKey() + ": " + e.getValue())
                                        .collect(Collectors.joining(", "))
                                        : "옵션 없음";

                                return String.format(
                                        "제품 ID: %d\n제품명: %s\n제조사: %s\n분류(type): %s\n가격: %s\n주요 옵션: %s",
                                        p.getId(),
                                        p.getName(),
                                        p.getManufacturer(),
                                        stdType,
                                        price,
                                        specs
                                );
                            })
                            .collect(Collectors.joining("\n---\n"));

                    return title + "\n" + block;
                })
                .collect(Collectors.joining("\n\n"));
    }


    private static String normalizeStdType(String raw) {
        if (raw == null) return "unknown";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "cpu", "processor": return "cpu";
            case "gpu", "graphics", "graphic_card", "vga", "video_card": return "vga";
            case "ram", "ram_memory", "memory", "dimm", "ddr", "ddr4", "ddr5": return "RAM";
            case "ssd", "nvme", "m2", "m_2", "solid_state_drive", "storage": return "ssd";
            case "hdd", "harddisk", "hard_drive": return "hdd";
            case "psu", "power", "power_supply", "smps": return "power";
            case "mainboard", "motherboard", "mb": return "mainboard";
            case "cooler", "cooling", "cpu_cooler": return "cooler";
            case "case", "chassis", "tower": return "case";
            default: return s;
        }
    }

    private Integer parsePrice(String priceStr) {
        if (priceStr == null) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractJsonOrRaw(String text) {
        if (text == null) return null;
        String cleaned = text
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1).trim();
        }
        return cleaned;
    }

    /* ====== 채팅방 조회/수정/삭제 ====== */

    @Transactional
    public ChatRoomResponse createChatRoom(String userId, String title) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));

        ChatRoom room = ChatRoom.builder()
                .user(user)
                .title(title != null ? title : "새 채팅방")
                .thread(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatRoomRepository.save(room);

        // 채팅방 단위 Thread 생성
        String threadId = createThread();
        room.setThread(threadId);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        log.info("chat room {} created with thread {}", room.getId(), threadId);
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
}
