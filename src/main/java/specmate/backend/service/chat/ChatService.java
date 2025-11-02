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
<<<<<<< HEAD
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
=======
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
>>>>>>> develop
        String reply;
        EstimateResult result;

        try {
            reply = assistantRunner.run(room.getThread(), userInput, ragContext, latestEstimate, type);
            log.info("[STEP4] Assistant 응답 수신 완료");

            // Step6. AI 응답(JSON) → 객체 변환 + 임베딩 매칭
            result = productEmbeddingService.processAiReply(reply, ragContext);
            log.info("[STEP5] Embedding 매칭 완료 ({}개 부품)", result.getProducts() != null ? result.getProducts().size() : 0);

        } catch (Exception e) {
<<<<<<< HEAD
            log.error("Assistant 호출 중 오류 발생", e);
            reply = "(응답 생성 실패)";
            result = EstimateResult.builder()
                    .selectType("conversation")
=======
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
>>>>>>> develop
                    .text(reply)
                    .build();
        }

<<<<<<< HEAD
        // Step7. 최종 트랜잭션 저장 및 응답 반환
        return chatTransactionService.persistChatResult(room, userInput, reply, result, ragContext, type);
=======
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

        AiEstimate estimate = aiEstimateService.createAiEstimate(room, assistantMsg, result);
        aiEstimateRepository.save(estimate);
        aiEstimateService.saveEstimateProducts(estimate, result);

        log.info("신규 견적 생성 완료: roomId={}, estimateId={}", room.getId(), estimate.getId());
        return estimate;
    }

    /** 재구성 요청 처리 → 생성된 AiEstimate 반환 */
    private AiEstimate handleReconfiguration(ChatRoom room, ChatMessage assistantMsg, EstimateResult result) {
        if (result == null || result.isEmpty() || result.isAllDefaults()) {
            log.info("재구성 요청이지만 유효한 견적 결과가 없어 저장을 생략합니다.");
            return null;
        }

        AiEstimate reconfigured = aiEstimateService.createAiEstimate(room, assistantMsg, result);
        aiEstimateRepository.save(reconfigured);
        aiEstimateService.saveEstimateProducts(reconfigured, result);

        log.info("견적 재구성 완료: roomId={}, newEstimateId={}", room.getId(), reconfigured.getId());
        return reconfigured;
    }

    /** 재구성 요청 여부 판단 */
    private boolean isReconfigurationRequest(String userInput) {
        String[] keywords = {"다시", "재구성", "수정", "바꿔", "변경", "업그레이드", "낮춰", "강화",
                "좀 더", "조용하게", "성능 높여", "성능 낮춰", "다른 걸로", "비슷하게"};
        return Arrays.stream(keywords).anyMatch(userInput::contains);
>>>>>>> develop
    }
}
