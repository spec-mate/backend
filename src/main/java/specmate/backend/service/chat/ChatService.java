package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.estimate.ai.EstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

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

    /** GPT 견적 처리 파이프라인 */
    @Transactional
    public EstimateResponse handleUserMessage(String roomId, String userInput) {
        // Thread 보장
        ChatRoom room = chatThreadService.ensureThread(roomId);

        // 사용자 메시지 저장
        chatMessageService.saveUserMessage(room, userInput);

        // RAG 컨텍스트 생성
        var ragContext = productRagService.buildRagContext(userInput);

        // GPT 호출
        String reply;
        try {
            reply = assistantRunner.run(room.getThread(), userInput, ragContext.getContextJson());
        } catch (Exception e) {
            log.error("GPT 호출 중 오류 발생", e);
            reply = "(GPT 응답 생성 중 오류가 발생했습니다.)";
        }

        if (reply == null || reply.isBlank()) {
            log.warn("GPT 응답이 비어 있음 → 기본 메시지 대체");
            reply = "(응답을 생성하지 못했습니다.)";
        }

        // GPT 응답 저장 (ChatMessage)
        ChatMessage assistantMsg = chatMessageService.saveAssistantMessage(room, reply);

        // GPT 응답 파싱
        EstimateResult result = estimateResultProcessor.parse(reply, null);

        // 비견적성 응답 판단
        if (result == null || result.isAllDefaults()) {
            log.info("비견적성 응답 → 텍스트 반환");
            return EstimateResponse.builder()
                    .text(reply)
                    .notes("비견적성 응답")
                    .build();
        }

        // 기존 견적 확인
        AiEstimate latestEstimate = aiEstimateRepository
                .findTopByChatRoomOrderByCreatedAtDesc(room)
                .orElse(null);

        // 신규/재구성 분기 처리
        AiEstimate savedEstimate;
        if (latestEstimate == null) {
            savedEstimate = handleNewEstimate(room, assistantMsg, result);
        } else if (isReconfigurationRequest(userInput)) {
            savedEstimate = handleReconfiguration(room, assistantMsg, result);
        } else {
            log.info("기존 견적이 존재 → 설명 모드로 판단");
            savedEstimate = null;
        }

        // 응답 변환 및 ID 주입
        EstimateResponse response = toResponse(result);
        if (savedEstimate != null) {
            response.setAiEstimateId(String.valueOf(savedEstimate.getId()));
        }

        return response;
    }

    /** EstimateResult → Response 변환 */
    private EstimateResponse toResponse(EstimateResult result) {
        if (result == null) return null;

        return EstimateResponse.builder()
                .buildName(result.getBuildName())
                .buildDescription(result.getBuildDescription())
                .totalPrice(result.getTotalPrice())
                .notes(result.getNotes())
                .text(result.getText())
                .anotherInputText(result.getAnotherInputText())
                .components(result.getProducts().stream()
                        .map(p -> EstimateResponse.ComponentResponse.builder()
                                .type(p.getType())
                                .matchedName(p.getMatchedName())
                                .description(p.getDescription())
                                .price(p.getPrice())
                                .image(p.getImage())
                                .build())
                        .toList())
                .build();
    }

    /** GPT의 ai_name과 DB matched_name 병합 */
    private void mergeMatchedNames(EstimateResult estimateResult,
                                   Map<String, EstimateResult.Product> fallbackMap) {
        if (estimateResult == null || estimateResult.getProducts() == null) return;

        for (EstimateResult.Product comp : estimateResult.getProducts()) {
            String key = comp.getType() != null ? comp.getType().toLowerCase(Locale.ROOT) : null;
            if (key == null) continue;

            EstimateResult.Product matched = fallbackMap.get(key);
            if (matched != null && matched.getMatchedName() != null) {
                comp.setMatchedName(matched.getMatchedName());
            }
        }
    }

    /** 신규 견적 생성 */
    private AiEstimate handleNewEstimate(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null || result.isEmpty() || result.isAllDefaults()) {
            log.info("비견적성 요청으로 판단 → 견적 저장 생략");
            return null;
        }

        AiEstimate estimate = aiEstimateService.createAiEstimate(room, assistantMsg, result);
        aiEstimateRepository.save(estimate);
        aiEstimateService.saveEstimateProducts(estimate, result);

        log.info("신규 견적 생성 완료: roomId={}, estimateId={}", room.getId(), estimate.getId());
        return estimate;
    }

    /**  재구성 견적 생성 */
    private AiEstimate handleReconfiguration(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null || result.isEmpty() || result.isAllDefaults()) {
            log.info("재구성 요청이지만 유효 견적 없음 → 저장 생략");
            return null;
        }

        AiEstimate reconfigured = aiEstimateService.createAiEstimate(room, assistantMsg, result);
        aiEstimateRepository.save(reconfigured);
        aiEstimateService.saveEstimateProducts(reconfigured, result);

        log.info("견적 재구성 완료: roomId={}, newEstimateId={}", room.getId(), reconfigured.getId());
        return reconfigured;
    }

    /** 재구성 요청 키워드 */
    private boolean isReconfigurationRequest(String userInput) {
        String[] keywords = {"다시", "재구성", "수정", "바꿔", "변경", "업그레이드", "낮춰", "강화",
                "좀 더", "조용하게", "성능 높여", "성능 낮춰", "다른 걸로", "비슷하게"};
        return Arrays.stream(keywords).anyMatch(userInput::contains);
    }
}
