package specmate.backend.config.vectorstore;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class CustomQdrantVectorStore extends QdrantVectorStore {

    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;
    private final String collectionName;

    public CustomQdrantVectorStore(QdrantClient qdrantClient,
                                   EmbeddingModel embeddingModel,
                                   String collectionName) {
        super(builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(false));
        this.qdrantClient = qdrantClient;
        this.embeddingModel = embeddingModel;
        this.collectionName = collectionName;
    }

    @Override
    public List<Document> doSimilaritySearch(SearchRequest request) {
        try {
            Filter filter = Filter.getDefaultInstance();

            float[] queryEmbedding = embeddingModel.embed(request.getQuery());

            SearchPoints searchPoints = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setLimit(request.getTopK())
                    .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
                    .addAllVector(EmbeddingUtils.toList(queryEmbedding))
                    .setFilter(filter)
                    .setScoreThreshold((float) request.getSimilarityThreshold())
                    .build();

            var response = qdrantClient.searchAsync(searchPoints).get();
            return response.stream().map(this::toDocument).toList();

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant similarity search failed", e);
        }
    }

    private Document toDocument(ScoredPoint point) {
        Map<String, Object> metadata = new HashMap<>();
        point.getPayloadMap().forEach((k, v) -> {
            Object extracted = extractValue(v);
            if (extracted != null) metadata.put(k, extracted);
        });

        metadata.put("distance", 1 - point.getScore());
        String content = (String) metadata.remove("doc_content");

        return Document.builder()
                .id(point.getId().getUuid())
                .text(content != null ? content : "")
                .metadata(metadata)
                .score((double) point.getScore())
                .build();
    }

    private Object extractValue(Value value) {
        if (value.hasStringValue()) return value.getStringValue();
        if (value.hasIntegerValue()) return value.getIntegerValue();
        if (value.hasDoubleValue()) return value.getDoubleValue();
        if (value.hasBoolValue()) return value.getBoolValue();
        return null;
    }
}
