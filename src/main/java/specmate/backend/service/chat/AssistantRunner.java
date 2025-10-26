package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantRunner {

    private final RestClient openAiRestClient;

    @Value("${openai.assistant.id:${OPENAI_ASSISTANT_ID:${openai_assistant_id:}}}")
    private String assistantId;

    /** 타임아웃 (기본 90초) */
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(90);

    /** Thread 생성 */
    public String createThread() {
        var res = openAiRestClient.post()
                .uri("/threads")
                .body(Map.of())
                .retrieve()
                .body(ThreadRes.class);

        if (res == null || res.id == null) {
            throw new IllegalStateException("Thread 생성 실패");
        }
        return res.id;
    }

    /** (1) 일반 실행 메서드 - 이미 완성된 systemPrompt + RAG JSON을 외부에서 결합했을 때 사용 */
    public String run(String threadId, String userMessage, String instructions) {
        addUserMessage(threadId, userMessage);
        String runId = startRun(threadId, instructions);
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

    /** Run 시작 (instructions는 결합된 프롬프트로 전달) */
    private String startRun(String threadId, String instructions) {
        var body = Map.of(
                "assistant_id", assistantId,
                "instructions", instructions
        );

        var res = openAiRestClient.post()
                .uri("/threads/{id}/runs", threadId)
                .body(body)
                .retrieve()
                .body(RunRes.class);

        if (res == null || res.id == null) {
            throw new IllegalStateException("Run 생성 실패");
        }
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

            if (run == null || run.status == null) continue;

            switch (run.status.toLowerCase()) {
                case "completed" -> {
                    log.debug("Run 완료됨 (runId={})", runId);
                    return;
                }
                case "requires_action" -> {
                    log.warn("Run이 requires_action 상태입니다. (tool_call 대기)");
                    throw new IllegalStateException("지원되지 않는 Run 상태: requires_action");
                }
                case "failed", "cancelled", "expired" -> {
                    throw new IllegalStateException("Run 실패 상태: " + run.status);
                }
            }

            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {}
        }
        throw new IllegalStateException("Run 대기 시간 초과");
    }

    /** 최신 assistant 메시지 가져오기 */
    private String fetchLatestAssistantText(String threadId) {
        var res = openAiRestClient.get()
                .uri(uri -> uri.path("/threads/{id}/messages")
                        .queryParam("limit", "10")
                        .build(threadId))
                .retrieve()
                .body(MessagesRes.class);

        if (res == null || res.data == null || res.data.isEmpty()) return null;

        // OpenAI는 data[0]이 최신 메시지
        return res.data.stream()
                .filter(m -> "assistant".equalsIgnoreCase(m.role))
                .map(Msg::extractTextMerged)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** 내부 record 구조 */
    private record ThreadRes(String id) {}

    private record RunRes(String id, String status) {}

    private record MessagesRes(List<Msg> data) {}

    private record Msg(String id, String role, List<Content> content) {
        /** 여러 text 블록을 모두 병합 */
        private String extractTextMerged() {
            if (content == null || content.isEmpty()) return null;
            return content.stream()
                    .filter(c -> c.text() != null && c.text().value() != null)
                    .map(c -> c.text().value())
                    .collect(Collectors.joining(""));
        }
    }

    private record Content(String type, Text text) {}
    private record Text(String value) {}
}
