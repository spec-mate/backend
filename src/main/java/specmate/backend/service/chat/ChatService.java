package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.estimate.ai.EstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.RagContext;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatRoom;
import specmate.backend.service.embedding.ProductEmbeddingService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    // Step1~6
    private final ChatThreadService chatThreadService;
    private final ChatInputService chatInputService;
    private final ChatClassifierService chatClassifierService;
    private final ProductRagService productRagService;
    private final AssistantRunner assistantRunner;
    private final ProductEmbeddingService productEmbeddingService;
    private final ChatTransactionService chatTransactionService;

    /**
     * 사용자 입력 전체 처리 파이프라인
     */
    @Transactional
    public EstimateResponse handleUserMessage(String roomId, String userInput) {
        // Step0. 채팅방 및 스레드 보장
        ChatRoom room = chatThreadService.ensureThread(roomId);

        // Step1. 사용자 입력 저장
        chatInputService.handleUserInput(roomId, userInput);

        // Step2. 분류 (A: 견적 생성, B: 일반 대화, C: 견적 재구성)
        String type = chatClassifierService.classify(userInput);
        log.info("[STEP2] 입력 분류 결과 = {}", type);

        // Step3. RAG 검색 (일상 대화 B는 스킵)
        RagContext ragContext = "B".equals(type)
                ? null
                : productRagService.buildRagContext(userInput);

        // Step4. 기존 견적 불러오기 (C일 때만)
        AiEstimate latestEstimate = chatTransactionService.findLatestEstimate(room);

        // Step5. Assistant 호출
        String reply;
        EstimateResult result;

        try {
            reply = assistantRunner.run(room.getThread(), userInput, ragContext, latestEstimate, type);
            log.info("[STEP4] Assistant 응답 수신 완료");

            // Step6. AI 응답(JSON) → 객체 변환 + 임베딩 매칭
            result = productEmbeddingService.processAiReply(reply, ragContext);
            log.info("[STEP5] Embedding 매칭 완료 ({}개 부품)", result.getProducts() != null ? result.getProducts().size() : 0);

        } catch (Exception e) {
            log.error("Assistant 호출 중 오류 발생", e);
            reply = "(응답 생성 실패)";
            result = EstimateResult.builder()
                    .selectType("conversation")
                    .text(reply)
                    .build();
        }

        // Step7. 최종 트랜잭션 저장 및 응답 반환
        return chatTransactionService.persistChatResult(room, userInput, reply, result, ragContext, type);
    }
}
