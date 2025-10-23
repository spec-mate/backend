package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.util.Arrays;

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

    /** 사용자 입력을 처리하고 GPT 견적 결과를 저장 */
    @Transactional
    public ChatMessage handleUserMessage(String roomId, String userInput) {

        // 채팅방 Thread 보장
        ChatRoom room = chatThreadService.ensureThread(roomId);

        // 사용자 메시지 저장
        chatMessageService.saveUserMessage(room, userInput);

        // RAG 컨텍스트 구성
        var ragContext = productRagService.buildRagContext(userInput);

        // 이전 견적 조회
        AiEstimate latestEstimate = aiEstimateRepository
                .findTopByChatRoomOrderByCreatedAtDesc(room)
                .orElse(null);

        // 프롬프트 구성
        String enhancedPrompt = buildPrompt(userInput, latestEstimate, ragContext);

        // GPT 호출
        String reply;
        try {
            reply = assistantRunner.run(room.getThread(), userInput, enhancedPrompt);
        } catch (Exception e) {
            log.error("GPT 호출 중 오류 발생", e);
            reply = "(GPT 응답 생성 중 오류가 발생했습니다.)";
        }

        // 빈 응답 처리
        if (reply == null || reply.isBlank()) {
            log.warn("GPT 응답이 비어 있음. 기본값으로 대체합니다.");
            reply = "(응답을 생성하지 못했습니다.)";
        }

        // GPT 응답 메시지 저장
        ChatMessage assistantMsg = chatMessageService.saveAssistantMessage(room, reply);

        // GPT 응답 파싱 (EstimateResult 변환)
        EstimateResult result = estimateResultProcessor.parse(reply, ragContext.getDtoFallbackMap());

        // 모두 미선택이면 설명 모드로 처리
        if (result != null && result.isAllDefaults()) {
            log.info("모든 부품이 '미선택' 상태 → 비견적성 설명 요청으로 판단합니다.");
            return assistantMsg;
        }

        // 견적 모드 분기
        if (latestEstimate == null) {
            // 신규 견적 생성 모드
            return handleNewEstimate(room, assistantMsg, result);
        } else if (isReconfigurationRequest(userInput)) {
            // 재구성 모드
            return handleReconfiguration(room, assistantMsg, result);
        } else {
            // 설명 모드 (JSON 생성 금지)
            log.info("기존 견적이 존재하므로 설명 모드로 동작합니다. 추가 견적은 생성하지 않습니다.");
            return assistantMsg;
        }
    }

    /** 신규 견적 생성 */
    private ChatMessage handleNewEstimate(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null || result.isEmpty() || result.isAllDefaults()) {
            log.info("비견적성 요청으로 판단되어 견적 저장을 생략합니다.");
            return assistantMsg;
        }

        AiEstimate estimate = aiEstimateService.createAiEstimate(room, assistantMsg, result);
        aiEstimateRepository.save(estimate);
        aiEstimateService.saveEstimateProducts(estimate, result);

        log.info("신규 견적 생성 완료: roomId={}, estimateId={}", room.getId(), estimate.getId());
        return assistantMsg;
    }

    /** 재구성 요청 처리 */
    private ChatMessage handleReconfiguration(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null || result.isEmpty() || result.isAllDefaults()) {
            log.info("재구성 요청이지만 유효한 견적 결과가 없어 저장을 생략합니다.");
            return assistantMsg;
        }

        AiEstimate reconfigured = aiEstimateService.createAiEstimate(room, assistantMsg, result);
        aiEstimateRepository.save(reconfigured);
        aiEstimateService.saveEstimateProducts(reconfigured, result);

        log.info("견적 재구성 완료: roomId={}, newEstimateId={}", room.getId(), reconfigured.getId());
        return assistantMsg;
    }

    /** 프롬프트 생성 로직 */
    private String buildPrompt(String userInput, AiEstimate latestEstimate, ProductRagService.RagContext ragContext) {
        String prompt = ragContext.getInstructions();

        if (latestEstimate != null) {
            try {
                AiEstimateResponse latestResponse = aiEstimateService.getEstimateWithProducts(latestEstimate.getId());
                prompt += "\n\n이전에 생성된 PC 견적(JSON)은 다음과 같습니다:\n";
                prompt += latestResponse.toJson();

                if (isReconfigurationRequest(userInput)) {
                    prompt += """
                    
                    \n\n사용자의 입력은 재구성 요청입니다.
                    위 견적을 기반으로 조건에 맞게 일부 부품만 변경하여 새로운 JSON 견적을 생성하십시오.
                    """;
                } else {
                    prompt += """
                    
                    \n\n위 견적을 기반으로 사용자의 질문이
                    소음, 발열, 전력, 업그레이드, 온도, 쿨링 등에 관한 경우,
                    절대 JSON을 다시 출력하지 말고 자연어 문장으로만 응답하십시오.
                    """;
                }
            } catch (Exception e) {
                log.warn("이전 견적 JSON 변환 중 오류 발생: {}", e.getMessage());
            }
        }

        return prompt;
    }

    /** 재구성 요청 여부 판단 */
    private boolean isReconfigurationRequest(String userInput) {
        String[] keywords = {"다시", "재구성", "수정", "바꿔", "변경", "업그레이드", "낮춰", "강화",
                "좀 더", "조용하게", "성능 높여", "성능 낮춰", "다른 걸로", "비슷하게"};
        return Arrays.stream(keywords).anyMatch(userInput::contains);
    }
}
