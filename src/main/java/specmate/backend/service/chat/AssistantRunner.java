package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantRunner {

    private final RestClient openAiRestClient;

    @Value("${openai.assistant.id:${OPENAI_ASSISTANT_ID:${openai_assistant_id:}}}")
    private String assistantId;

    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(60);

    /** Thread 생성 */
    public String createThread() {
        var res = openAiRestClient.post().uri("/threads")
                .body(Map.of())
                .retrieve().body(ThreadRes.class);
        if (res == null || res.id == null)
            throw new IllegalStateException("Thread 생성 실패");
        return res.id;
    }

    /** 실행: 사용자 메시지 추가 → Run 시작 → 완료 대기 → 결과 반환 */
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
                .retrieve().toBodilessEntity();
    }

    /** Assistant 실행 (Run 생성) */
    private String startRun(String threadId, String instructions) {
        var body = Map.of("assistant_id", assistantId);

        var res = openAiRestClient.post()
                .uri("/threads/{id}/runs", threadId)
                .body(body)
                .retrieve().body(RunRes.class);

        if (res == null || res.id == null)
            throw new IllegalStateException("Run 생성 실패");
        return res.id;
    }

    /** Run 완료 대기 */
    private void waitUntilCompleted(String threadId, String runId) {
        long end = System.currentTimeMillis() + RUN_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < end) {
            var run = openAiRestClient.get().uri("/threads/{t}/runs/{r}", threadId, runId)
                    .retrieve().body(RunRes.class);
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

    /** 최신 메시지(assistant 응답) 가져오기 */
    private String fetchLatestAssistantText(String threadId) {
        var res = openAiRestClient.get()
                .uri(uri -> uri.path("/threads/{id}/messages")
                        .queryParam("limit", "10").build(threadId))
                .retrieve().body(MessagesRes.class);

        if (res == null || res.data == null) return null;

        return res.data.stream()
                .filter(m -> "assistant".equalsIgnoreCase(m.role))
                .findFirst()
                .map(Msg::extractText)
                .orElse(null);
    }

    /** Record 구조들 (최신 OpenAI API 호환) */

    private record ThreadRes(String id) {}

    private record RunRes(String id, String status) {}

    private record MessagesRes(List<Msg> data) {}

    private record Msg(String id, String role, List<Content> content) {
        private String extractText() {
            if (content == null || content.isEmpty()) return null;
            // content[].text.value 형태 대응
            Content c = content.get(0);
            if (c.text() == null) return null;
            return c.text().value();
        }
    }

    private record Content(String type, Text text) {}
    private record Text(String value) {}
}
