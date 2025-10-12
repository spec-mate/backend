package specmate.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;

@Configuration
public class OpenAiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Bean
    public OpenAiApi openAiApi() {
        return new OpenAiApi(openAiApiKey);
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingModel(openAiApi);
    }
}
