package specmate.backend.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.entity.Product;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${OPENAI_API_KEY}")
    private String apiKey;

    @Value("${OPENAI_ASSISTANT_ID}")
    private String assistantId;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(200, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .build();

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Thread 생성 */
    public String createThread() throws IOException {
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/threads")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(RequestBody.create("{}", JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Thread 생성 실패: " + response);
            }
            String resBody = response.body().string();
            JsonNode root = objectMapper.readTree(resBody);
            return root.get("id").asText();
        }
    }

    /** Thread에 메시지 추가 */
    private void addMessageToThread(String threadId, String prompt, List<Product> products) throws IOException {
        // DB에서 조회된 부품 목록 → 문자열 변환
        String productList = products.stream()
                .map(p -> {
                    String price = "가격 정보 없음";
                    if (p.getLowestPrice() != null && p.getLowestPrice().get("price") != null) {
                        price = p.getLowestPrice().get("price").toString() + "원";
                    }
                    return "- " + p.getType() + ": " + p.getName() + " (" + price + ")";
                })
                .collect(Collectors.joining("\n"));

        String finalPrompt =
                "당신은 \"PC 견적 구성 전문가\"입니다.\n" +
                        "아래는 데이터베이스에서 검색된 부품 후보입니다.\n" +
                        "이 부품들을 참고하여 사용자의 요구에 맞는 최적의 PC 견적을 JSON으로 작성하세요.\n\n" +
                        "규칙:\n" +
                        "- components[].type 필드는 반드시 'CPU', 'MainBoard', 'RAM', 'GPU', 'SSD', 'Power', 'Cooler', 'Case' 중 하나를 사용하세요.\n" +
                        "- build_name: 사용자의 요구를 한 줄로 요약한 견적 이름을 작성하세요.\n" +
                        "- build_description: 해당 견적의 목적과 특징을 설명하는 문장을 작성하세요.\n" +
                        "- notes: 추가적인 코멘트, 업그레이드/다운그레이드 옵션, 부품 선택 이유 등을 최소 1줄 이상 작성하세요.\n" +
                        "- 절대 'category'라는 키를 쓰지 말고 반드시 'type' 키를 사용하세요.\n" +
                        "- name과 price는 반드시 아래 검색 결과에서 제공된 값을 그대로 사용하세요.\n" +
                        "- description은 사용자의 요구(화이트 감성, 게이밍, 영상편집 등)를 반영하여 새롭게 작성하세요.\n" +
                        "- total은 모든 price를 합산해 \"1,830,000원\"처럼 문자열로 표시하세요.\n" +
                        "- another_input_text에는 사용자가 추가로 물어볼 수 있는 질문을 3~5개 생성하세요.\n" +
                        "- JSON 이외의 텍스트는 절대 출력하지 마세요.\n\n" +
                        "[검색된 부품 데이터]\n" + productList + "\n\n" +
                        "사용자 입력: " + prompt;

        Map<String, Object> messageJson = Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "text", "text", finalPrompt))
        );

        RequestBody body = RequestBody.create(objectMapper.writeValueAsString(messageJson), JSON);

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/messages")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Message 추가 실패: " + response);
            }
        }
    }

    /** Run 생성 */
    private String createRun(String threadId) throws IOException {
        Map<String, Object> runJson = Map.of("assistant_id", assistantId);
        RequestBody runBody = RequestBody.create(objectMapper.writeValueAsString(runJson), JSON);

        Request runRequest = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/runs")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(runBody)
                .build();

        try (Response response = client.newCall(runRequest).execute()) {
            String resBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Run 생성 실패: " + resBody);
            }
            JsonNode root = objectMapper.readTree(resBody);
            return root.get("id").asText();
        }
    }

    /** Run 완료 대기 후 결과 가져오기 */
    private String waitForRunCompletion(String threadId, String runId) throws IOException, InterruptedException {
        String status = "in_progress";
        while (!status.equals("completed") && !status.equals("failed")) {
            Thread.sleep(2000);

            Request checkRequest = new Request.Builder()
                    .url("https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("OpenAI-Beta", "assistants=v2")
                    .build();

            try (Response response = client.newCall(checkRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Run 상태 확인 실패: " + response);
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                status = root.get("status").asText();
            }
        }

        if (status.equals("failed")) {
            throw new IOException("Run 실행 실패");
        }

        // 최종 메시지 가져오기
        Request messagesRequest = new Request.Builder()
                .url("https://api.openai.com/v1/threads/" + threadId + "/messages")
                .header("Authorization", "Bearer " + apiKey)
                .header("OpenAI-Beta", "assistants=v2")
                .build();

        try (Response response = client.newCall(messagesRequest).execute()) {
            String resBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("메시지 조회 실패: " + resBody);
            }
            JsonNode root = objectMapper.readTree(resBody);

            JsonNode data = root.get("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode lastMessage = data.get(0);
                return lastMessage.get("content").get(0).get("text").get("value").asText();
            }
            return null;
        }
    }

    /** 최종 GPT 호출 */
    public GPTResponse callGptApi(String prompt, List<Product> products, String threadId) throws IOException {
        addMessageToThread(threadId, prompt, products);
        String runId = createRun(threadId);

        String assistantReply;
        try {
            assistantReply = waitForRunCompletion(threadId, runId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Run 대기 중 인터럽트 발생", e);
        }

        GPTResponse gptResponse = new GPTResponse();
        GPTResponse.Choice choice = new GPTResponse.Choice();
        GPTResponse.Message msg = new GPTResponse.Message();
        msg.setContent(assistantReply);

        choice.setMessage(msg);
        gptResponse.setChoices(List.of(choice));

        return gptResponse;
    }
}
