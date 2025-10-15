package specmate.backend.service.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.entity.Product;
import specmate.backend.entity.ProductEmbedding;
import specmate.backend.repository.embedding.ProductEmbeddingRepository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final EmbeddingModel embeddingModel;
    private final ProductEmbeddingRepository embeddingRepository;

    /** 카테고리별 의미 기반 검색 */
    @Transactional(readOnly = true)
    public List<Product> searchSimilarProductsByCategory(String query, int limitPerCategory) {
        Map<String, List<String>> typeMap = Map.of(
                "case", List.of("case"),
                "cpu", List.of("cpu"),
                "gpu", List.of("vga"),
                "ram", List.of("ram", "RAM"),
                "storage", List.of("ssd"),
                "psu", List.of("power"),
                "motherboard", List.of("mainboard"),
                "cooler", List.of("cooler"),
                "hdd", List.of("hdd")
        );

        List<String> gptTypes = List.of(
                "case", "cpu", "gpu", "ram", "storage", "psu", "motherboard", "cooler", "hdd"
        );

        List<Product> allResults = new ArrayList<>();

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(query));
            float[] queryVector = response.getResults().get(0).getOutput();
            String queryVectorString = toPgVectorString(queryVector);

            for (String gptType : gptTypes) {
                List<String> aliases = typeMap.getOrDefault(gptType, List.of(gptType));
                List<Product> resultsForType = new ArrayList<>();

                for (String alias : aliases) {
                    List<ProductEmbedding> found = embeddingRepository
                            .findNearestEmbeddingsByType(queryVectorString, alias.toLowerCase(), limitPerCategory);

                    resultsForType.addAll(found.stream()
                            .map(ProductEmbedding::getProduct)
                            .filter(this::isValidProduct)
                            .toList());
                }

                if (!resultsForType.isEmpty()) {
                    allResults.addAll(resultsForType);
                    log.info("[{}] 검색된 제품 {}개", gptType, resultsForType.size());
                } else {
                    log.warn("[{}] 검색 결과 없음", gptType);
                }
            }

        } catch (Exception e) {
            log.error("카테고리별 검색 실패: {}", e.getMessage());
        }

        return allResults;
    }

    private boolean isValidProduct(Product p) {
        if (p == null || p.getLowestPrice() == null) return false;
        Object priceObj = p.getLowestPrice().get("price");
        if (priceObj == null) return false;

        try {
            int price = Integer.parseInt(priceObj.toString().replaceAll("[^0-9]", ""));
            return price >= 10000 && price <= 3000000;
        } catch (Exception e) {
            return false;
        }
    }

    private String toPgVectorString(float[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
