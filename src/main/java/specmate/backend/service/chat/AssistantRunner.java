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

    @Value("${openai.assistant.id.1}")
    private String classifierAssistantId;

    @Value("${openai.assistant.id.2}")
    private String estimateAssistantId;

    @Value("${openai.assistant.id.3}")
    private String conversationAssistantId;

    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1초
    private static final Duration ACTIVE_RUN_CHECK_TIMEOUT = Duration.ofSeconds(30);
    private static final long ACTIVE_RUN_CHECK_INTERVAL_MS = 500; // 0.5초

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
        String runId = startRun(threadId, assistantId, instructions);
        waitUntilCompleted(threadId, runId);
        return fetchLatestAssistantText(threadId);
    }

    /** 특정 Assistant로 실행 (메시지 추가 없이) */
    public String runWithAssistant(String threadId, String specificAssistantId) {
        return runWithAssistant(threadId, specificAssistantId, null);
    }

    /** 특정 Assistant로 실행 (instructions 포함) */
    public String runWithAssistant(String threadId, String specificAssistantId, String instructions) {
        String runId = startRun(threadId, specificAssistantId, instructions);
        waitUntilCompleted(threadId, runId);
        return fetchLatestAssistantText(threadId);
    }

    /** 분류기 Assistant 실행 */
    public String runClassifier(String threadId) {
        return runWithAssistant(threadId, classifierAssistantId);
    }

    /** 견적 생성 Assistant 실행 */
    public String runEstimate(String threadId) {
        return runWithAssistant(threadId, estimateAssistantId);
    }

    /** 대화형 Assistant 실행 */
    public String runConversation(String threadId) {
        return runWithAssistant(threadId, conversationAssistantId);
    }

    /** Assistant ID getter */
    public String getEstimateAssistantId() {
        return estimateAssistantId;
    }

    /** 사용자 메시지만 추가 (Run 실행 없이) */
    public void addMessage(String threadId, String userMessage) {
        addUserMessage(threadId, userMessage);
    }

    /** 사용자 메시지 추가 (활성 run 체크 포함) */
    private void addUserMessage(String threadId, String text) {
        // 활성 run이 있으면 완료될 때까지 대기
        waitForActiveRunsToComplete(threadId);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                openAiRestClient.post()
                        .uri("/threads/{id}/messages", threadId)
                        .body(Map.of("role", "user", "content", text))
                        .retrieve().toBodilessEntity();

                log.debug("메시지 추가 성공 - threadId: {}", threadId);
                return;

            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";

                // 활성 run 오류인 경우 재시도 전에 다시 대기
                if (errorMsg.contains("while a run") && errorMsg.contains("is active")) {
                    log.warn("메시지 추가 실패 (활성 run 감지, 시도 {}/{}) - threadId: {}",
                            attempt, MAX_RETRY_ATTEMPTS, threadId);

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        waitForActiveRunsToComplete(threadId);
                        try {
                            Thread.sleep(INITIAL_RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("메시지 추가 중단됨", ie);
                        }
                    }
                } else {
                    // 다른 종류의 오류는 바로 예외 발생
                    log.error("메시지 추가 실패 - threadId: {}, 에러: {}", threadId, errorMsg);
                    throw new IllegalStateException("메시지 추가 실패: " + errorMsg, e);
                }
            }
        }

        log.error("메시지 추가 최종 실패 - threadId: {}, 모든 재시도 소진", threadId);
        throw new IllegalStateException("메시지 추가 실패: " + lastException.getMessage(), lastException);
    }

    /** 활성 run이 있으면 완료될 때까지 대기 */
    private void waitForActiveRunsToComplete(String threadId) {
        try {
            long end = System.currentTimeMillis() + ACTIVE_RUN_CHECK_TIMEOUT.toMillis();

            while (System.currentTimeMillis() < end) {
                // 현재 thread의 runs 목록 조회
                var runsRes = openAiRestClient.get()
                        .uri(uri -> uri.path("/threads/{id}/runs")
                                .queryParam("limit", "10")
                                .build(threadId))
                        .retrieve()
                        .body(RunsListRes.class);

                if (runsRes == null || runsRes.data == null || runsRes.data.isEmpty()) {
                    log.debug("활성 run 없음 - threadId: {}", threadId);
                    return;
                }

                // 활성 상태(in_progress, queued, requires_action)인 run이 있는지 확인
                boolean hasActiveRun = runsRes.data.stream()
                        .anyMatch(run -> Set.of("in_progress", "queued", "requires_action")
                                .contains(run.status));

                if (!hasActiveRun) {
                    log.debug("모든 run 완료됨 - threadId: {}", threadId);
                    return;
                }

                log.info("활성 run 대기 중 - threadId: {}", threadId);
                Thread.sleep(ACTIVE_RUN_CHECK_INTERVAL_MS);
            }

            log.warn("활성 run 대기 시간 초과 - threadId: {}", threadId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("활성 run 대기 중단됨 - threadId: {}", threadId);
        } catch (Exception e) {
            log.warn("활성 run 체크 중 오류 (무시하고 계속 진행) - threadId: {}, 에러: {}",
                    threadId, e.getMessage());
        }
    }

    /** Assistant 실행 (재시도 포함) */
    private String startRun(String threadId, String specificAssistantId, String instructions) {
        // Run 시작 전 활성 run 대기
        waitForActiveRunsToComplete(threadId);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("assistant_id", specificAssistantId);

        if (instructions != null && !instructions.isBlank()) {
            body.put("instructions", instructions);
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.debug("Run 시작 시도 {}/{} - assistantId: {}", attempt, MAX_RETRY_ATTEMPTS, specificAssistantId);

                var res = openAiRestClient.post()
                        .uri("/threads/{id}/runs", threadId)
                        .body(body)
                        .retrieve().body(RunRes.class);

                if (res == null || res.id == null) {
                    throw new IllegalStateException("Run 생성 실패: 응답이 null입니다");
                }

                log.debug("Run 시작 성공 - runId: {}", res.id);
                return res.id;

            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";

                // 활성 run 오류인 경우 재시도 전에 다시 대기
                if (errorMsg.contains("already has an active run")) {
                    log.warn("Run 시작 실패 (활성 run 감지, 시도 {}/{}) - threadId: {}, assistantId: {}",
                            attempt, MAX_RETRY_ATTEMPTS, threadId, specificAssistantId);

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        waitForActiveRunsToComplete(threadId);
                        try {
                            Thread.sleep(INITIAL_RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Run 시작 중단됨", ie);
                        }
                    }
                } else {
                    log.warn("Run 시작 실패 (시도 {}/{}) - assistantId: {}, 에러: {}",
                            attempt, MAX_RETRY_ATTEMPTS, specificAssistantId, e.getMessage());

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1); // Exponential backoff
                        log.info("{}ms 후 재시도합니다...", delayMs);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Run 시작 중단됨", ie);
                        }
                    }
                }
            }
        }

        log.error("Run 시작 최종 실패 - assistantId: {}, 모든 재시도 소진", specificAssistantId);
        throw new IllegalStateException("Run 생성 실패: " + lastException.getMessage(), lastException);
    }

    /** Run 완료 대기 */
    private void waitUntilCompleted(String threadId, String runId) {
        long end = System.currentTimeMillis() + RUN_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < end) {
            var run = openAiRestClient.get().uri("/threads/{t}/runs/{r}", threadId, runId)
                    .retrieve().body(RunRes.class);
            if (run == null) continue;

            if ("completed".equalsIgnoreCase(run.status)) return;
            if (Set.of("failed", "cancelled", "expired").contains(run.status)) {
                // 상세한 에러 정보 로깅
                String errorMsg = "Run 상태: " + run.status;
                if (run.last_error != null) {
                    errorMsg += " | 에러 코드: " + run.last_error.code
                            + " | 에러 메시지: " + run.last_error.message;
                }
                log.error("OpenAI Assistant Run 실패 - threadId: {}, runId: {}, {}",
                        threadId, runId, errorMsg);

                // Run Steps 조회하여 추가 정보 로깅
                try {
                    logRunSteps(threadId, runId);
                } catch (Exception e) {
                    log.warn("Run Steps 조회 실패: {}", e.getMessage());
                }

                throw new IllegalStateException(errorMsg);
            }

            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {}
        }
        throw new IllegalStateException("Run 대기 시간 초과");
    }

    /** Run Steps 조회 및 로깅 (디버깅용) */
    private void logRunSteps(String threadId, String runId) {
        try {
            var stepsRes = openAiRestClient.get()
                    .uri("/threads/{t}/runs/{r}/steps", threadId, runId)
                    .retrieve()
                    .body(RunStepsRes.class);

            if (stepsRes != null && stepsRes.data != null && !stepsRes.data.isEmpty()) {
                log.error("=== Run Steps 상세 정보 ===");
                for (var step : stepsRes.data) {
                    log.error("Step ID: {}, Type: {}, Status: {}",
                            step.id, step.type, step.status);
                    if (step.last_error != null) {
                        log.error("  └─ Step Error: {} - {}",
                                step.last_error.code, step.last_error.message);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Run Steps 조회 중 예외: {}", e.getMessage());
        }
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

    /** Record 구조들 */
    private record ThreadRes(String id) {}

    private record RunRes(String id, String status, LastError last_error) {}

    private record LastError(String code, String message) {}

    private record RunsListRes(List<RunRes> data) {}

    private record RunStepsRes(List<RunStep> data) {}

    private record RunStep(String id, String type, String status, LastError last_error) {}

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
