package specmate.backend.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import specmate.backend.entity.Assistant;
import specmate.backend.repository.chat.AssistantRepository;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssistantService {

    @Value("${OPENAI_API_KEY}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AssistantRepository assistantRepository;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Assistant 생성 (OpenAI 호출 + DB 저장) */
    @Transactional
    public Assistant createAssistant(String name, String description, String rolePrompt, String model) throws IOException {
        Map<String, Object> payload = Map.of(
                "name", name,
                "instructions", rolePrompt,
                "model", model
        );

        RequestBody body = RequestBody.create(objectMapper.writeValueAsString(payload), JSON);

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/assistants")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("OpenAI-Beta", "assistants=v2")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Assistant 생성 실패: " + responseBody);
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String assistantId = root.get("id").asText();

            // DB 저장
            Assistant assistant = Assistant.builder()
                    .id(assistantId)
                    .name(name)
                    .description(description)
                    .instruction(rolePrompt)
                    .model(model)
                    .isActive(true)
                    .build();

            return assistantRepository.save(assistant);
        }
    }

    /** 활성화된 Assistant 조회 */
    public Assistant getActiveAssistant() {
        return assistantRepository.findByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("활성화된 Assistant 없음"));
    }
}
