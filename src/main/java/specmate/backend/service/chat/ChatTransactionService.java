package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.estimate.ai.EstimateResponse;
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

    public AiEstimate findLatestEstimate(ChatRoom room) {
        return aiEstimateRepository
                .findTopByChatRoomOrderByCreatedAtDesc(room)
                .orElse(null);
    }

    @Transactional
    public EstimateResponse persistChatResult(ChatRoom room,
                                              String userInput,
                                              String reply,
                                              EstimateResult estimateResult,
                                              RagContext ragContext,
                                              String type) {

        // 사용자 메시지 저장
        chatMessageService.saveUserMessage(room, userInput);

        // 어시스턴트 메시지 저장
        ChatMessage assistantMsg = chatMessageService.saveAssistantMessage(room, reply);

        // 견적 생성/재구성 시 DB 저장
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

            AiEstimate saved = aiEstimateService.createAiEstimate(room, assistantMsg, estimateResult, ragDataList);
            estimateResult.setAiEstimateId(saved.getId().toString());
        }

        // 결과 응답 반환
        return toResponse(estimateResult);
    }

    private EstimateResponse toResponse(EstimateResult result) {
        if (result == null) return null;

        return EstimateResponse.builder()
                .aiEstimateId(result.getAiEstimateId())
                .buildName(result.getBuildName())
                .buildDescription(result.getBuildDescription())
                .totalPrice(result.getTotalPrice())
                .notes(result.getNotes())
                .text(result.getText())
                .anotherInputText(result.getAnotherInputText())
                .components(result.getProducts() == null
                        ? Collections.emptyList()
                        : result.getProducts().stream()
                        .map(p -> EstimateResponse.ComponentResponse.builder()
                                .type(p.getType())
                                .name(p.getName())
                                .description(p.getDescription())
                                .detail(EstimateResponse.ComponentResponse.Detail.builder()
                                        .price(p.getDetail() != null ? p.getDetail().getPrice() : "0")
                                        .image(p.getDetail() != null ? p.getDetail().getImage() : "")
                                        .build())
                                .build())
                        .toList())
                .build();
    }
}
