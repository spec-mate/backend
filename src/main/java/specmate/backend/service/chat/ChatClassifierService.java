package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.RagContext;
import specmate.backend.processor.EstimateResultProcessor;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatClassifierService {
    private final AssistantRunner assistantRunner;
    private final EstimateResultProcessor estimateResultProcessor;

    /** 사용자 입력을 기반으로 A/B/C 분류 */
    public String classify(String userInput) {
        String lower = userInput.toLowerCase();
        if (lower.matches(".*(다시|재구성|수정|바꿔|업그레이드|낮춰).*")) return "C";
        if (lower.matches(".*(견적|추천|조립|pc|컴퓨터).*")) return "A";
        return "B";
    }

    /** AI 응답 문자열 → EstimateResult 변환 */
    public EstimateResult parseEstimateResult(String reply, RagContext ragContext) {
        try {
            return estimateResultProcessor.parse(
                    reply,
                    ragContext != null ? ragContext.getDtoFallbackMap() : Map.of()
            );
        } catch (Exception e) {
            return EstimateResult.builder()
                    .selectType("conversation")
                    .build();
        }
    }
}
