package specmate.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiConfig {

    @Value("${spring.ai.openai.api-key:${OPENAI_API_KEY:${openai_api_key:${SPRING_AI_OPENAI_API_KEY:}}}}")
    private String openAiApiKey;

    @Bean
    public OpenAiApi openAiApi() {
        requireApiKey(openAiApiKey);
        return new OpenAiApi(openAiApiKey);
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingModel(openAiApi);
    }

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

    private static void requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API Key가 설정되지 않았습니다. " +
                            "application.yml의 spring.ai.openai.api-key 또는 환경변수(OPENAI_API_KEY / openai_api_key / SPRING_AI_OPENAI_API_KEY)를 설정하세요."
            );
        }
    }
}
