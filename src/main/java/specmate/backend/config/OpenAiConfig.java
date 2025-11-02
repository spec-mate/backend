package specmate.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
public class OpenAiConfig {

    @Value("${spring.ai.openai.api-key:${OPENAI_API_KEY:${openai_api_key:${SPRING_AI_OPENAI_API_KEY:}}}}")
    private String openAiApiKey;

    @Value("${openai.assistant.id.1}")
    private String assistantId1;

    @Value("${openai.assistant.id.2}")
    private String assistantId2;

    @Value("${openai.assistant.id.3}")
    private String assistantId3;

    /** REST 클라이언트 (Assistants API용) */
    @Bean
    public RestClient openAiRestClient() {
        requireApiKey(openAiApiKey);

        return RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + openAiApiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("OpenAI-Beta", "assistants=v2")
                .build();
    }

    /** 여러 Assistant ID 등록 */
    @Bean
    public List<String> openAiAssistantIds() {
        return List.of(assistantId1, assistantId2, assistantId3);
    }

    /** API 키 유효성 검사 */
    private static void requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API Key가 설정되지 않았습니다. " +
                            "application.yml의 spring.ai.openai.api-key 또는 " +
                            "환경변수(OPENAI_API_KEY / openai_api_key / SPRING_AI_OPENAI_API_KEY)를 설정하세요."
            );
        }
    }
}
