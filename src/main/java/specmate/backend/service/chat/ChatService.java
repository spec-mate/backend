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

    /** 사용자 입력을 처리하고 GPT 견적 결과를 저장 */
    @Transactional
    public EstimateResponse handleUserMessage(String roomId, String userInput) {
        // Thread 보장
        ChatRoom room = chatThreadService.ensureThread(roomId);

        // 사용자 메시지 저장
        chatMessageService.saveUserMessage(room, userInput);

        // RAG 컨텍스트 (userInput 기반)
        var ragContext = productRagService.buildRagContext(userInput);

        // 최근 견적 조회
        AiEstimate latestEstimate = aiEstimateRepository
                .findTopByChatRoomOrderByCreatedAtDesc(room)
                .orElse(null);

        // GPT 호출
        String reply;
        try {
            reply = assistantRunner.run(
                    room.getThread(),
                    userInput,
                    ragContext.getInstructions()
            );
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

        // GPT 응답 파싱 (EstimateResult 생성: ai_name 포함)
        EstimateResult estimateResult = estimateResultProcessor.parse(reply, ragContext.getDtoFallbackMap());

        if (estimateResult == null || estimateResult.isAllDefaults()) {
            log.info("비견적성 요청으로 판단 → JSON 응답 대신 일반 텍스트 응답 반환");
            return EstimateResponse.builder()
                    .text(reply)
                    .notes("비견적성 응답")
                    .build();
        }

        // DB 매칭 수행 (matched_name 채우기)
        var refinedRagContext = productRagService.buildRagContext(estimateResult);
        mergeMatchedNames(estimateResult, refinedRagContext.getDtoFallbackMap());

        // 견적 저장 및 ID 추출
        AiEstimate savedEstimate = null;
        if (latestEstimate == null) {
            savedEstimate = handleNewEstimate(room, assistantMsg, estimateResult);
        } else if (isReconfigurationRequest(userInput)) {
            savedEstimate = handleReconfiguration(room, assistantMsg, estimateResult);
        } else {
            log.info("기존 견적이 존재 → 설명 모드로 동작");
        }

        // 응답 변환 + ID 주입
        EstimateResponse response = toResponse(estimateResult);
        if (savedEstimate != null) {
            response.setAiEstimateId(String.valueOf(savedEstimate.getId()));
        }

        return response;
    }

    /** EstimateResult → EstimateResponse 변환 */
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
                                .price(p.getPrice())
                                .description(p.getDescription())
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
            if (matched != null) {
                comp.setMatchedName(matched.getMatchedName());
            }
        }
    }

    /** 신규 견적 생성 → 생성된 AiEstimate 반환 */
    private AiEstimate handleNewEstimate(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null || result.isEmpty() || result.isAllDefaults()) {
            log.info("비견적성 요청으로 판단되어 견적 저장을 생략합니다.");
            return null;
        }

        // Legacy 흐름: RAG 없이 견적 생성 (빈 제품 리스트 전달)
        AiEstimate estimate = aiEstimateService.createAiEstimate(room, assistantMsg, result, java.util.Collections.emptyList());
        aiEstimateRepository.save(estimate);
        aiEstimateService.saveEstimateProducts(estimate, result, java.util.Collections.emptyList());

        log.info("신규 견적 생성 완료: roomId={}, estimateId={}", room.getId(), estimate.getId());
        return estimate;
    }

    /** 재구성 요청 처리 → 생성된 AiEstimate 반환 */
    private AiEstimate handleReconfiguration(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null || result.isEmpty() || result.isAllDefaults()) {
            log.info("재구성 요청이지만 유효한 견적 결과가 없어 저장을 생략합니다.");
            return null;
        }

        // Legacy 흐름: RAG 없이 견적 생성 (빈 제품 리스트 전달)
        AiEstimate reconfigured = aiEstimateService.createAiEstimate(room, assistantMsg, result, java.util.Collections.emptyList());
        aiEstimateRepository.save(reconfigured);
        aiEstimateService.saveEstimateProducts(reconfigured, result, java.util.Collections.emptyList());

        log.info("견적 재구성 완료: roomId={}, newEstimateId={}", room.getId(), reconfigured.getId());
        return reconfigured;
    }

    /** 재구성 요청 여부 판단 */
    private boolean isReconfigurationRequest(String userInput) {
        String[] keywords = {"다시", "재구성", "수정", "바꿔", "변경", "업그레이드", "낮춰", "강화",
                "좀 더", "조용하게", "성능 높여", "성능 낮춰", "다른 걸로", "비슷하게"};
        return Arrays.stream(keywords).anyMatch(userInput::contains);
    }
}
