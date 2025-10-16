package specmate.backend.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QdrantConfig {

    @Value("${QDRANT_HOST}")
    private String host;

    @Value("${QDRANT_PORT}")
    private int port;

    @Value("${QDRANT_API_KEY}")
    private String apiKey;

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, port, true)
                .withApiKey(apiKey)
                .build();

        return new QdrantClient(grpcClient);
    }
}
