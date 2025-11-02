package specmate.backend.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.RagContext;
import specmate.backend.entity.ChatRoom;
import specmate.backend.service.embedding.ProductEmbeddingService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatThreadService chatThreadService;
    private final ChatInputService chatInputService;
    private final ChatClassifierService chatClassifierService;
    private final ProductRagService productRagService;
    private final AssistantRunner assistantRunner;
    private final ProductEmbeddingService productEmbeddingService;
    private final ChatTransactionService chatTransactionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ChatMessageResponse handleUserMessage(String roomId, String userInput) {
        ChatRoom room = chatThreadService.ensureThread(roomId);
        chatInputService.handleUserInput(roomId, userInput);

        String type = chatClassifierService.classify(userInput);
        log.info("[STEP2] 입력 분류 결과 = {}", type);

        RagContext ragContext = null;
        EstimateResult prevEstimate = null;

        // Step3: RAG 빌드
        if ("A".equals(type)) {
            // 새 견적 요청
            ragContext = productRagService.buildRagContext(userInput);

        } else if ("C".equals(type)) {
            // 재구성 요청 → 기존 견적 JSON 불러오기
            String estimateJson = chatTransactionService.findLatestEstimateJson(roomId);

            if (estimateJson != null && !estimateJson.isBlank()) {
                try {
                    prevEstimate = objectMapper.readValue(estimateJson, EstimateResult.class);
                    ragContext = productRagService.buildRagContext(prevEstimate);
                    log.info("[STEP3] 기존 견적 기반 RAG Context 구성 완료");
                } catch (Exception e) {
                    log.warn("[STEP3] 기존 견적 파싱 실패, 새 RAG 검색으로 대체", e);
                    ragContext = productRagService.buildRagContext(userInput);
                }
            } else {
                log.warn("[STEP3] 기존 견적 없음, 새 RAG 검색으로 대체");
                ragContext = productRagService.buildRagContext(userInput);
            }

        } else {
            // 일반 대화 (B)
            ragContext = null;
        }

        // Step4: Assistant 호출 및 결과 처리
        String reply;
        EstimateResult result;

        try {
            reply = assistantRunner.run(room.getThread(), userInput, ragContext, null, type);
            log.info("[STEP4] Assistant 응답 수신 완료");

            result = productEmbeddingService.processAiReply(reply, ragContext);
            log.info("[STEP5] Embedding 매칭 완료 ({}개 부품)",
                    result.getProducts() != null ? result.getProducts().size() : 0);

        } catch (Exception e) {
            log.error("Assistant 호출 중 오류 발생", e);
            reply = "(응답 생성 실패)";
            result = EstimateResult.builder()
                    .selectType("conversation")
                    .build();
        }

        // Step6: 최종 트랜잭션 저장 및 응답 반환
        return chatTransactionService.persistChatResult(room, userInput, reply, result, ragContext, type);
    }
}
