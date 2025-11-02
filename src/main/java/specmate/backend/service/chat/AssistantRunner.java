package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import specmate.backend.dto.estimate.ai.RagContext;
import specmate.backend.entity.AiEstimate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantRunner {

    private final RestClient openAiRestClient;

    // 각각의 Assistant 역할 매핑
    @Value("${openai.assistant.id.1}")
    private String classifierAssistantId;

    @Value("${openai.assistant.id.2}")
    private String generatorAssistantId;

    @Value("${openai.assistant.id.3}")
    private String generalAssistantId;

    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(60);

    /** Thread 생성 */
    public String createThread() {
        var res = openAiRestClient.post()
                .uri("/threads")
                .body(Map.of())
                .retrieve()
                .body(ThreadRes.class);

        if (res == null || res.id == null)
            throw new IllegalStateException("Thread 생성 실패");
        return res.id;
    }

    /**
     * 3단계 역할 기반 실행
     */
    public String run(String threadId,
                      String userInput,
                      RagContext ragContext,
                      AiEstimate latestEstimate,
                      String type) {

        String assistantId;
        String prompt;

        switch (type) {
            case "A" -> { // 새 견적 생성
                assistantId = generatorAssistantId;
                prompt = buildPromptForGenerator(userInput, ragContext);
            }
            case "C" -> { // 기존 견적 재구성
                assistantId = generatorAssistantId;
                prompt = buildPromptForRebuilder(userInput, ragContext, latestEstimate);
            }
            default -> { // 일반 대화 / 설명 (B)
                assistantId = generalAssistantId;
                prompt = buildPromptForGeneral(userInput);
            }
        }

        log.info("Assistant 호출 시작 (type={}, id={})", type, assistantId);
        addUserMessage(threadId, prompt);
        String runId = startRun(threadId, assistantId);
        waitUntilCompleted(threadId, runId);
        return fetchLatestAssistantText(threadId);
    }

    /** 사용자 메시지 추가 */
    private void addUserMessage(String threadId, String text) {
        openAiRestClient.post()
                .uri("/threads/{id}/messages", threadId)
                .body(Map.of("role", "user", "content", text))
                .retrieve()
                .toBodilessEntity();
    }

<<<<<<< HEAD
    /** Assistant Run 생성 */
    private String startRun(String threadId, String assistantId) {
=======
    /** Assistant 실행 */
    private String startRun(String threadId, String instructions) {
        var body = Map.of("assistant_id", assistantId);

>>>>>>> develop
        var res = openAiRestClient.post()
                .uri("/threads/{id}/runs", threadId)
                .body(Map.of("assistant_id", assistantId))
                .retrieve()
                .body(RunRes.class);

        if (res == null || res.id == null)
            throw new IllegalStateException("Run 생성 실패");
        return res.id;
    }

    /** Run 완료 대기 */
    private void waitUntilCompleted(String threadId, String runId) {
        long end = System.currentTimeMillis() + RUN_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < end) {
            var run = openAiRestClient.get()
                    .uri("/threads/{t}/runs/{r}", threadId, runId)
                    .retrieve()
                    .body(RunRes.class);

            if (run == null) continue;

            if ("completed".equalsIgnoreCase(run.status)) return;
            if (Set.of("failed", "cancelled", "expired").contains(run.status))
                throw new IllegalStateException("Run 상태: " + run.status);

            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {}
        }
        throw new IllegalStateException("Run 대기 시간 초과");
    }

    /** 최신 Assistant 응답 텍스트 반환 */
    private String fetchLatestAssistantText(String threadId) {
        var res = openAiRestClient.get()
                .uri(uri -> uri.path("/threads/{id}/messages")
                        .queryParam("limit", "10")
                        .build(threadId))
                .retrieve()
                .body(MessagesRes.class);

        if (res == null || res.data == null) return null;

        return res.data.stream()
                .filter(m -> "assistant".equalsIgnoreCase(m.role))
                .findFirst()
                .map(Msg::extractText)
                .orElse(null);
    }

<<<<<<< HEAD
    @Async("assistantExecutor")
    public CompletableFuture<String> runAsync(String threadId,
                                              String userInput,
                                              RagContext ragContext,
                                              AiEstimate latestEstimate,
                                              String type) {
        // 별도 스레드에서 run() 실행
        return CompletableFuture.supplyAsync(() ->
                run(threadId, userInput, ragContext, latestEstimate, type)
        );
    }


    /* =======================
       프롬프트 빌더
       ======================= */

    private String buildPromptForGenerator(String userInput, RagContext ragContext) {
        return "[SpecMate 견적 생성]\n입력: " + userInput +
                "\nRAG 데이터:\n" + (ragContext != null ? ragContext.getJson() : "[]");
    }

    private String buildPromptForRebuilder(String userInput, RagContext ragContext, AiEstimate estimate) {
        String prevEstimateSummary = "{}";
        if (estimate != null) {
            prevEstimateSummary = String.format("""
            {
              "title": "%s",
              "description": "%s",
              "totalPrice": %d
            }
            """,
                    safe(estimate.getTitle()),
                    safe(estimate.getDescription()),
                    estimate.getTotalPrice() != null ? estimate.getTotalPrice() : 0
            );
        }

        return "[SpecMate 견적 재구성]\n입력: " + userInput +
                "\n기존 견적 요약: " + prevEstimateSummary +
                "\nRAG 데이터:\n" + (ragContext != null ? ragContext.getJson() : "[]");
    }


    private String buildPromptForGeneral(String userInput) {
        return "[일반 대화/설명 요청]\n입력: " + userInput;
    }


    private String safe(String s) {
        return s == null ? "" : s.replace("\"", "\\\"").trim();
    }

=======
    /** Record 구조들 */
>>>>>>> develop
    private record ThreadRes(String id) {}
    private record RunRes(String id, String status) {}
    private record MessagesRes(List<Msg> data) {}
    private record Msg(String id, String role, List<Content> content) {
        private String extractText() {
            if (content == null || content.isEmpty()) return null;
            Content c = content.get(0);
            if (c.text() == null) return null;
            return c.text().value();
        }
    }
    private record Content(String type, Text text) {}
    private record Text(String value) {}
}
