package specmate.backend.service.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.CosineSimilarity;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.entity.Product;
import specmate.backend.entity.ProductEmbedding;
import specmate.backend.repository.embedding.ProductEmbeddingRepository;
import specmate.backend.repository.product.ProductRepository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final EmbeddingModel embeddingModel;
    private final ProductEmbeddingRepository embeddingRepository;

    private double lastSimilarityScore = 0.0;

    public double getLastSimilarityScore() {
        return lastSimilarityScore;
    }

    /** AI 견적 매칭용 - 프롬프트/임베딩 신뢰 기반 의미 유사도 검색 서버는 오직 벡터 검색과 스코어링만 수행한다. */
    @Transactional(readOnly = true)
    public Optional<Product> findMostSimilarProduct(String query, String type) {
        try {
            if (query == null || query.isBlank()) return Optional.empty();

            // 입력 문장을 그대로 임베딩
            String inputText = query.trim();
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(inputText));
            float[] queryVector = response.getResults().get(0).getOutput();
            String queryVectorString = toPgVectorString(queryVector);

            // 동일 카테고리 내에서 의미 기반 검색
            List<ProductEmbedding> nearest = embeddingRepository.findNearestByNormalizedType(queryVectorString, type, 20);

            if (nearest.isEmpty()) {
                log.warn("[{}] 타입 내 임베딩 결과 없음 → 전역 폴백 시도", type);
                return fallbackGlobalSearch(queryVectorString, inputText);
            }

            // 상위 후보 중 가장 유사한 항목 선택
            Optional<Product> best = nearest.stream()
                    .map(ProductEmbedding::getProduct)
                    .filter(Objects::nonNull)
                    .filter(p -> type == null || p.getType().equalsIgnoreCase(type))
                    .max(Comparator.comparingDouble(p -> estimateSimilarity(inputText, p.getName())));

            if (best.isPresent()) {
                Product p = best.get();
                lastSimilarityScore = estimateSimilarity(inputText, p.getName());
                if (lastSimilarityScore >= 0.2) {
                    log.info("임베딩 매칭 성공 '{}' → '{}' (type={}, 유사도={})",
                            query, p.getName(), type, String.format("%.2f", lastSimilarityScore));
                    return Optional.of(p);
                }
            }

            // 폴백 검색
            return fallbackGlobalSearch(queryVectorString, inputText);

        } catch (Exception e) {
            log.error("임베딩 기반 검색 실패 ({}): {}", query, e.getMessage(), e);
            lastSimilarityScore = 0.0;
            return Optional.empty();
        }
    }

    /** 전역 검색 (타입 무시) */
    private Optional<Product> fallbackGlobalSearch(String queryVectorString, String query) {
        List<ProductEmbedding> global = embeddingRepository.findNearestEmbeddings(queryVectorString, 10);

        for (ProductEmbedding candidate : global) {
            Product p = candidate.getProduct();
            if (p == null) continue;

            lastSimilarityScore = estimateSimilarity(query, p.getName());
            if (lastSimilarityScore >= 0.25) {
                log.info("전역 폴백 매칭 성공 '{}' → '{}' (type={}, 유사도={})",
                        query, p.getName(), p.getType(), String.format("%.2f", lastSimilarityScore));
                return Optional.of(p);
            }
        }

        log.warn("'{}' 전역 검색에서도 적절한 매칭을 찾지 못했습니다.", query);
        return Optional.empty();
    }

    /** 카테고리별 의미 기반 검색 (RAG 컨텍스트용) */
    @Transactional(readOnly = true)
    public List<Product> searchSimilarProductsByCategory(String query, int limitPerCategory) {
        List<String> categories = List.of("case", "cpu", "vga", "ram", "power", "ssd", "mainboard", "cooler", "hdd");
        List<Product> allResults = new ArrayList<>();

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(query));
            float[] queryVector = response.getResults().get(0).getOutput();
            String queryVectorString = toPgVectorString(queryVector);

            for (String category : categories) {
                List<ProductEmbedding> found = embeddingRepository
                        .findNearestByNormalizedType(queryVectorString, category, limitPerCategory);

                List<Product> results = found.stream()
                        .map(ProductEmbedding::getProduct)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (!results.isEmpty()) {
                    log.debug("[{}] 검색 결과 {}개", category, results.size());
                    allResults.addAll(results);
                }
            }

        } catch (Exception e) {
            log.error("카테고리별 검색 실패: {}", e.getMessage(), e);
        }

        return allResults;
    }

    /** 문자열 유사도 계산 (Cosine 기반) */
    private double estimateSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;

        CosineSimilarity cosine = new CosineSimilarity();
        Map<CharSequence, Integer> left = getTermFrequencyMap(a);
        Map<CharSequence, Integer> right = getTermFrequencyMap(b);
        Double sim = cosine.cosineSimilarity(left, right);
        return sim != null ? sim : 0.0;
    }

    private Map<CharSequence, Integer> getTermFrequencyMap(String input) {
        Map<CharSequence, Integer> tf = new HashMap<>();
        for (char c : input.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c)) continue;
            tf.merge(String.valueOf(c), 1, Integer::sum);
        }
        return tf;
    }

    private String toPgVectorString(float[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
