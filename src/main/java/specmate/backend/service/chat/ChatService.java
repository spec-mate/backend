package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.service.estimate.ai.AiEstimateService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatThreadService chatThreadService;
    private final ChatMessageService chatMessageService;
    private final ProductRagService productRagService;
    private final AssistantRunner assistantRunner;
    private final EstimateResultProcessor estimateResultProcessor;
    private final AiEstimateService aiEstimateService;
    private final AiEstimateRepository aiEstimateRepository;

     /** 사용자 입력을 처리하고 GPT 견적 결과를 저장하는 핵심 트랜잭션 */
    @Transactional
    public ChatMessage handleUserMessage(String roomId, String userInput) {

        // Thread 보장
        ChatRoom room = chatThreadService.ensureThread(roomId);

        // 사용자 메시지 저장
        ChatMessage userMsg = chatMessageService.saveUserMessage(room, userInput);

        // RAG 검색 및 후보 데이터 구성
        var ragContext = productRagService.buildRagContext(userInput);

        // OpenAI Assistant 호출
        String reply;
        try {
            reply = assistantRunner.run(room.getThread(), userInput, ragContext.getInstructions());
        } catch (Exception e) {
            log.error("GPT 호출 중 오류 발생", e);
            reply = "(GPT 응답 생성 중 오류가 발생했습니다.)";
        }

        if (reply == null || reply.isBlank()) {
            log.warn("GPT 응답이 비어 있음. 기본값으로 대체합니다.");
            reply = "(응답을 생성하지 못했습니다.)";
        }

        // GPT 응답 메시지 저장
        ChatMessage assistantMsg = chatMessageService.saveAssistantMessage(room, reply);

        // GPT 응답 파싱
        EstimateResult result = estimateResultProcessor.parse(reply, ragContext.getDtoFallbackMap());

        // 견적 생성 및 저장
        AiEstimate estimate = aiEstimateService.createAiEstimate(room, assistantMsg, result);
        aiEstimateRepository.save(estimate);

        // 견적-제품 매핑
        aiEstimateService.saveEstimateProducts(estimate, result);

        log.info("채팅 요청 처리 완료: roomId={}, estimateId={}", roomId, estimate.getId());
        return assistantMsg;
    }
}
