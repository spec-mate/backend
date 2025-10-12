package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.document.Document;
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
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiEstimateRepository aiEstimateRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final EstimateResultProcessor estimateResultProcessor;

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    /** 사용자 입력 → RAG 검색 + GPT 견적 응답 통합 처리 */
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

        // RAG: PgVector에서 유사 문서 검색
        var results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(content)
                        .topK(5)
                        .build()
        );

        String context = results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        String systemPrompt = """
        당신은 "PC 견적 구성 전문가"입니다.  
        사용자의 요구사항(예: 화이트 감성, 게이밍, 영상 편집, 저소음 등)에 맞춰  
        아래 데이터베이스(PostgreSQL pgvector)에서 제공된 부품 후보들을 조합하여 최적의 PC 견적을 작성하세요.
        
        ---
        
        ### 역할 및 제약
        - 당신은 오직 **아래 제공된 부품 데이터**만을 사용해야 합니다.  
        - 절대 새로운 부품명, 가격, 브랜드, 모델명을 생성하지 마세요.
        - 출력은 반드시 **완전한 JSON 객체 하나**로만 구성해야 하며,  
          JSON 이외의 설명, 문장, 코드 블록(````), 주석, 마크다운 등은 출력하지 마세요.
        - JSON 구조나 key 이름을 임의로 변경하지 마세요.
        - 모든 필드는 문자열(String)로 작성하세요.
        
        ---
        
        ### JSON 작성 규칙
        
        1. **name**  
           - 반드시 제공된 검색 결과의 `name` 값을 그대로 사용하세요.
        
        2. **price**  
           - 반드시 제공된 가격을 그대로 사용하세요.  
           - `"300,000원"`처럼 숫자 뒤에 `"원"`을 붙인 문자열 형태로 표기하세요.
        
        3. **description**  
           - 사용자의 요청(예: 화이트 감성, 게이밍, 영상편집 등)을 반영하여  
             부품의 역할과 선택 이유를 간결하게 작성하세요.  
           - 예: `"화이트 감성의 미니타워 케이스로, 세련된 RGB 감성을 구현합니다."`
        
        4. **total**  
           - 모든 부품의 price를 합산하여 `"1,830,000원"`처럼 문자열로 표시하세요.  
           - 3자리마다 쉼표(,)를 포함해야 합니다.  
           - total은 반드시 main 안의 모든 price 값의 합계와 정확히 일치해야 합니다.
        
        5. **note**  
           - 전체 견적의 특징이나 사용 목적을 1~2문장으로 요약하세요.
        
        6. **another_input_text**  
           - 사용자가 추가로 물어볼 수 있는 질문을 3~5개 생성하세요.  
           - 예: `"이 PC로 4K 영상 편집이 가능한가요?"`, `"화이트 감성으로 유지보수하기 쉬운가요?"` 등.
        
        ---
        
        ### JSON 출력 형식
        
        {
          "intro": "이 견적은 화이트 감성 게이밍을 위해 구성되었습니다.",
          "main": {
            "case": { "name": "NZXT H510 White", "description": "화이트 감성의 깔끔한 디자인으로 RGB 시스템에 잘 어울립니다.", "price": "120,000원" },
            "cpu": { "name": "Intel i5-13400F", "description": "6코어 12스레드로 게이밍과 멀티태스킹에 적합합니다.", "price": "300,000원" },
            "gpu": { "name": "NVIDIA RTX 4070", "description": "고해상도 게이밍을 위한 최적의 그래픽카드입니다.", "price": "800,000원" },
            "ram": { "name": "Corsair Vengeance RGB White 16GBx2", "description": "화이트 감성과 RGB 조합으로 화려한 시스템 구현이 가능합니다.", "price": "150,000원" },
            "storage": { "name": "Samsung 980 Pro 1TB", "description": "고속 NVMe SSD로 빠른 부팅과 로딩 속도를 제공합니다.", "price": "120,000원" },
            "psu": { "name": "Seasonic Focus GX-750 White", "description": "안정적인 전력 공급과 화이트 감성 디자인이 돋보입니다.", "price": "140,000원" },
            "motherboard": { "name": "ASUS Prime Z690-P White", "description": "화이트 감성에 맞춘 세련된 디자인의 고성능 메인보드입니다.", "price": "200,000원" }
          },
          "total": "1,830,000원",
          "note": "화이트 감성과 게이밍 성능을 동시에 고려한 고급 견적입니다.",
          "another_input_text": [
            "이 견적에 어울리는 모니터를 추천해 주세요.",
            "RGB 조명 커스터마이징이 가능한가요?",
            "이 구성으로 배틀그라운드를 최고 옵션으로 플레이할 수 있나요?"
          ]
        }
        
        ---
        
        ### 순서 고정
        - main 키 내부의 부품 순서는 반드시 다음 순서를 따라야 합니다:  
          `case → cpu → gpu → ram → storage → psu → motherboard`
        
        ---
        
        ### 입력
        검색된 부품 데이터:
        {component_text}
        
        사용자 요구사항:
        {req_text}
        
        ---
        
        ### 출력 제약
        - 출력은 반드시 JSON Only  
        - JSON 외 텍스트 절대 금지  
        - 키 누락 금지 (`intro`, `main`, `total`, `note`, `another_input_text`)  
        - 가격 계산 정확성 유지  
        - 유효한 JSON 구조만 허용
        """;


        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage("질문: " + content + "\n\n참고 데이터:\n" + context)
        ));

        var response = chatModel.call(prompt);
        String reply = response.getResult().getOutput().getText();

        // GPT 응답 저장
        ChatMessage assistantMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(reply)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(assistantMsg);

        // GPT 결과 파싱 → 견적 데이터 저장
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

    /** 채팅방 생성 */
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

    /** 채팅방 내 메시지 전체 조회 */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatMessages(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        List<ChatMessage> messages = chatMessageRepository.findByChatRoomOrderByCreatedAtAsc(room);
        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }

    /** 사용자별 채팅방 목록 조회 */
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getUserChatRooms(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));

        return chatRoomRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(ChatRoomResponse::fromEntity)
                .toList();
    }

    /** 단일 채팅방 조회 */
    @Transactional(readOnly = true)
    public ChatRoomResponse getChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));
        return ChatRoomResponse.fromEntity(room);
    }

    /** 채팅방 제목 수정 */
    @Transactional
    public ChatRoomResponse updateChatRoom(String roomId, ChatRoomRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        room.setTitle(request.getTitle());
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        return ChatRoomResponse.fromEntity(room);
    }

    /** 채팅방 삭제 (관련 메시지 및 견적도 함께 삭제) */
    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 없음"));

        chatMessageRepository.deleteAllByChatRoom(room);
        aiEstimateRepository.deleteAllByChatRoom(room);
        chatRoomRepository.delete(room);
    }

    /** 문자열 가격을 정수로 변환 */
    private Integer parsePrice(String priceStr) {
        if (priceStr == null) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
