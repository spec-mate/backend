package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.RagContext;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTransactionService {

    private final ChatMessageService chatMessageService;
    private final AiEstimateService aiEstimateService;
    private final AiEstimateRepository aiEstimateRepository;

    /** 최신 AiEstimate 엔티티 전체 반환 */
    public AiEstimate findLatestEstimate(ChatRoom room) {
        return aiEstimateRepository
                .findTopByChatRoomOrderByCreatedAtDesc(room)
                .orElse(null);
    }

    /** 최신 견적 JSON 문자열만 조회 (엔티티 수정 불필요) */
    @Transactional(readOnly = true)
    public String findLatestEstimateJson(String roomId) {
        try {
            String json = aiEstimateRepository.findLatestEstimateJson(roomId);
            if (json == null) {
                log.warn("[RAG] 최근 견적 JSON이 존재하지 않습니다. roomId={}", roomId);
            }
            return json;
        } catch (Exception e) {
            log.error("[RAG] 최근 견적 JSON 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /** GPT 대화 결과 + 견적 트랜잭션 처리 */
    @Transactional
    public ChatMessageResponse persistChatResult(ChatRoom room,
                                                 String userInput,
                                                 String reply,
                                                 EstimateResult estimateResult,
                                                 RagContext ragContext,
                                                 String type) {

        // Step1. 사용자 메시지 저장
        chatMessageService.saveUserMessage(room, userInput);

        // Step2. GPT 응답 메시지 저장
        ChatMessage assistantMsg = chatMessageService.saveAssistantMessage(room, reply);

        // Step3. 견적 생성/재구성일 경우 DB 저장
        if (!"B".equals(type) && estimateResult != null && !estimateResult.isAllDefaults()) {

            List<Map<String, Object>> ragDataList = Optional.ofNullable(ragContext)
                    .map(RagContext::getDtoFallbackMap)
                    .orElse(Map.of())
                    .values()
                    .stream()
                    .map(p -> Map.<String, Object>of(
                            "type", p.getType(),
                            "name", p.getName(),
                            "price", p.getDetail() != null ? p.getDetail().getPrice() : "0",
                            "image", p.getDetail() != null ? p.getDetail().getImage() : ""
                    ))
                    .collect(Collectors.toList());

            // Estimate 저장
            AiEstimate saved = aiEstimateService.createAiEstimate(room, assistantMsg, estimateResult, ragDataList);
            estimateResult.setAiEstimateId(saved.getId().toString());

            // JSON 변환 후 ChatMessage에 주입
            assistantMsg.setParsedJson(estimateResult.toMap());

            // ai_estimate_id 반영
            if (assistantMsg.getParsedJson() != null) {
                assistantMsg.getParsedJson().put("ai_estimate_id", saved.getId().toString());
            }
        }

        // 최종 응답 반환
        return ChatMessageResponse.fromEntity(assistantMsg);
    }
}
