package specmate.backend.repository.embedding;

import specmate.backend.entity.ProductEmbedding;

import java.util.List;
import java.util.Map;

public interface ProductEmbeddingRepositoryCustom {
    void bulkInsert(List<Map<String, Object>> records);
    List<ProductEmbedding> searchBySimilarity(String userInput, int limit);
}
