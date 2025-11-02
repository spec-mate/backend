package specmate.backend.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import specmate.backend.config.vectorstore.CustomQdrantVectorStore;

@Configuration
@RequiredArgsConstructor
public class QdrantConfig {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${spring.ai.vectorstore.qdrant.host}")
    private String host;

    @Value("${spring.ai.vectorstore.qdrant.port}")
    private int port;

    @Value("${spring.ai.vectorstore.qdrant.api-key}")
    private String apiKey;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collectionName;

    /** Qdrant Cloud용 gRPC Client (TLS 필수) */
    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, port, true);
        if (apiKey != null && !apiKey.isBlank()) {
            builder = builder.withApiKey(apiKey);
        }
        return new QdrantClient(builder.build());
    }

    /** OpenAI 임베딩 모델 등록 */
    @Bean
    public EmbeddingModel embeddingModel() {
        OpenAiApi api = new OpenAiApi(openAiApiKey);
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED);
    }

    /** Spring AI Qdrant VectorStore */
    @Bean
    public QdrantVectorStore qdrantVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return new CustomQdrantVectorStore(qdrantClient, embeddingModel, collectionName);
    }
}
