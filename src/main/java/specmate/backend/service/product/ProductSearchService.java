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

    /** AI 견적 매칭용 - 의미 기반 유사도 검색 */
    @Transactional(readOnly = true)
    public Optional<Product> findMostSimilarProduct(String query, String type) {
        try {
            if (query == null || query.isBlank()) return Optional.empty();

            // 입력 문장 임베딩
            String inputText = query.trim();
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(inputText));
            float[] queryVector = response.getResults().get(0).getOutput();
            String queryVectorString = toPgVectorString(queryVector);

            // type이 없을 경우 자동 추론
            if (type == null || type.isBlank() || type.equalsIgnoreCase("unknown")) {
                type = inferCategoryFromText(query);
            }

            // 해당 type 내 검색
            List<ProductEmbedding> nearest = embeddingRepository.findNearestByNormalizedType(queryVectorString, type, 20);

            if (nearest.isEmpty()) {
                log.warn("[{}] 타입 내 임베딩 결과 없음 → 전역 폴백 시도", type);
                return fallbackGlobalSearch(queryVectorString, inputText);
            }

            Optional<Product> best = nearest.stream()
                    .map(ProductEmbedding::getProduct)
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingDouble(p -> estimateSimilarity(inputText, p.getName())));

            if (best.isPresent()) {
                Product p = best.get();
                lastSimilarityScore = estimateSimilarity(inputText, p.getName());
                log.info("임베딩 매칭 성공 '{}' → '{}' (type={}, 유사도={})",
                        query, p.getName(), type, String.format("%.2f", lastSimilarityScore));
                return Optional.of(p);
            }

            return fallbackGlobalSearch(queryVectorString, inputText);

        } catch (Exception e) {
            log.error("임베딩 검색 실패 ({}): {}", query, e.getMessage(), e);
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

        log.warn("'{}' 전역 검색에서도 매칭 실패", query);
        return Optional.empty();
    }

    /** AI 견적 매칭용 - 모든 카테고리별 의미 기반 유사도 검색 */
    @Transactional(readOnly = true)
    public List<Product> searchSimilarProductsByCategory(String query, int limitPerCategory) {
        try {
            if (query == null || query.isBlank()) return List.of();

            // 입력 문장 임베딩
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(query));
            float[] queryVector = response.getResults().get(0).getOutput();
            String queryVectorString = toPgVectorString(queryVector);

            // 9개 카테고리 순회
            List<String> ALL_TYPES = List.of(
                    "case", "cpu", "vga", "ram", "ssd", "power", "mainboard", "cooler", "hdd"
            );

            List<Product> results = new ArrayList<>();

            for (String type : ALL_TYPES) {
                List<ProductEmbedding> found = embeddingRepository
                        .findNearestByNormalizedType(queryVectorString, type, limitPerCategory);

                // 각 카테고리의 최상위 1개만 선택
                found.stream()
                        .map(ProductEmbedding::getProduct)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .ifPresent(results::add);
            }

            log.info("카테고리별 검색 결과: {}/9개 카테고리에서 제품 매칭 성공", results.size());
            return results;

        } catch (Exception e) {
            log.error("카테고리별 검색 실패: {}", e.getMessage(), e);
            return List.of();
        }
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

    /** pgvector 호환 문자열 생성 */
    private String toPgVectorString(float[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }

    /** 간단한 키워드 기반 카테고리 추론 */
    private String inferCategoryFromText(String text) {
        if (text == null || text.isBlank()) return "unknown";
        String lower = text.toLowerCase(Locale.ROOT);

        if (lower.contains("cpu") || lower.contains("프로세서") || lower.contains("라이젠") || lower.contains("인텔"))
            return "cpu";
        if (lower.contains("gpu") || lower.contains("그래픽") || lower.contains("vga") || lower.contains("rtx") || lower.contains("gtx"))
            return "vga";
        if (lower.contains("ram") || lower.contains("메모리") || lower.contains("ddr"))
            return "ram";
        if (lower.contains("ssd") || lower.contains("nvme") || lower.contains("저장"))
            return "ssd";
        if (lower.contains("hdd") || lower.contains("하드"))
            return "hdd";
        if (lower.contains("파워") || lower.contains("psu") || lower.contains("전원"))
            return "power";
        if (lower.contains("보드") || lower.contains("mainboard") || lower.contains("motherboard") || lower.contains("메인보드"))
            return "mainboard";
        if (lower.contains("쿨러") || lower.contains("cooler") || lower.contains("팬"))
            return "cooler";
        if (lower.contains("케이스") || lower.contains("case") || lower.contains("chassis"))
            return "case";

        return "unknown";
    }
}
