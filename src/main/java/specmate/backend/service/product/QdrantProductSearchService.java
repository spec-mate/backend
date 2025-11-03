package specmate.backend.service.product;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import specmate.backend.entity.Product;
import specmate.backend.repository.product.ProductRepository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QdrantProductSearchService {

    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;
    private final ProductRepository productRepository;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collectionName;

    private double lastSimilarityScore = 0.0;

    public double getLastSimilarityScore() {
        return lastSimilarityScore;
    }

    /**
     * Qdrant를 사용한 의미 기반 제품 검색
     * @param query 검색 쿼리
     * @param type 제품 타입 (카테고리)
     * @return 가장 유사한 제품
     */
    public Optional<Product> findMostSimilarProduct(String query, String type) {
        try {
            if (query == null || query.isBlank()) {
                return Optional.empty();
            }

            // 쿼리를 임베딩 벡터로 변환
            float[] queryVector = embedQuery(query);

            // Qdrant에서 벡터 검색
            List<Points.ScoredPoint> searchResults = searchVectors(queryVector, type, 20);

            if (searchResults.isEmpty()) {
                log.warn("[{}] 타입 내 검색 결과 없음 → 전역 폴백 시도", type);
                return fallbackGlobalSearch(queryVector, query);
            }

            // 검색 결과에서 Product ID 추출 및 DB 조회
            List<Integer> productIds = searchResults.stream()
                    .map(point -> {
                        try {
                            long pointId = point.getId().getNum();

                            // Point ID가 0이면 payload에서 추출 시도
                            if (pointId == 0) {
                                var payload = point.getPayloadMap();
                                if (!payload.isEmpty()) {
                                    if (payload.containsKey("id") && payload.get("id").hasIntegerValue()) {
                                        return (int) payload.get("id").getIntegerValue();
                                    }
                                    if (payload.containsKey("product_id") && payload.get("product_id").hasIntegerValue()) {
                                        return (int) payload.get("product_id").getIntegerValue();
                                    }
                                }
                            }

                            return (int) pointId;
                        } catch (Exception e) {
                            log.warn("Invalid product ID: {}", point.getId());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(id -> id != 0)
                    .collect(Collectors.toCollection(ArrayList::new));

            if (productIds.isEmpty()) {
                return Optional.empty();
            }

            // DB에서 제품 정보 조회
            List<Product> products = productRepository.findAllById(productIds);

            if (products.isEmpty()) {
                return Optional.empty();
            }

            // 가장 유사도가 높은 제품 선택
            Optional<Product> best = products.stream()
                    .filter(p -> type == null || p.getType().equalsIgnoreCase(type))
                    .findFirst();

            if (best.isPresent()) {
                // 첫 번째 결과의 유사도 점수 저장
                lastSimilarityScore = searchResults.get(0).getScore();
                Product p = best.get();
                log.info("Qdrant 매칭 성공 '{}' → '{}' (type={}, 유사도={})",
                        query, p.getName(), type, String.format("%.2f", lastSimilarityScore));
                return Optional.of(p);
            }

            return fallbackGlobalSearch(queryVector, query);

        } catch (Exception e) {
            log.error("Qdrant 검색 실패 ({}): {}", query, e.getMessage(), e);
            lastSimilarityScore = 0.0;
            return Optional.empty();
        }
    }

    /**
     * 카테고리별 의미 기반 검색 (RAG 컨텍스트용)
     * @param query 검색 쿼리
     * @param limitPerCategory 카테고리당 검색 제한 수
     * @return 유사한 제품 리스트
     */
    public List<Product> searchSimilarProductsByCategory(String query, int limitPerCategory) {
        List<String> categories = List.of("case", "cpu", "vga", "ram", "power", "ssd", "mainboard", "cooler", "hdd");
        List<Product> allResults = new ArrayList<>();

        try {
            float[] queryVector = embedQuery(query);
            log.info("임베딩 벡터 생성 완료: {} 차원", queryVector.length);

            for (String category : categories) {
                List<Points.ScoredPoint> searchResults = searchVectors(queryVector, category, limitPerCategory);
                log.info("[{}] Qdrant 벡터 검색 결과: {} 개 포인트", category, searchResults.size());

                // Payload에서 name 추출하여 DB 조회
                List<String> productNames = searchResults.stream()
                        .map(point -> {
                            try {
                                var payload = point.getPayloadMap();
                                if (payload.containsKey("name") && payload.get("name").hasStringValue()) {
                                    return payload.get("name").getStringValue();
                                }
                                return null;
                            } catch (Exception e) {
                                log.warn("[{}] Product name 추출 실패: {}", category, e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                log.info("[{}] 추출된 Product Names: {}", category, productNames);

                if (!productNames.isEmpty()) {
                    // Name으로 DB에서 제품 조회
                    List<Product> products = productRepository.findByNameInAndType(productNames, category);
                    log.info("[{}] DB에서 조회된 제품: {} 개", category, products.size());

                    if (products.isEmpty()) {
                        log.warn("[{}] Qdrant에는 있지만 DB에는 없는 제품: {}", category, productNames);
                    } else {
                        log.info("[{}] 조회된 제품 목록: {}", category,
                                products.stream().map(Product::getName).collect(Collectors.toList()));
                    }
                    allResults.addAll(products);
                }
            }

            log.info("전체 카테고리 검색 완료: 총 {} 개 제품", allResults.size());

        } catch (Exception e) {
            log.error("카테고리별 Qdrant 검색 실패: {}", e.getMessage(), e);
        }

        return allResults;
    }

    /**
     * 전역 검색 (타입 무시)
     */
    private Optional<Product> fallbackGlobalSearch(float[] queryVector, String query) {
        try {
            List<Points.ScoredPoint> globalResults = searchVectors(queryVector, null, 10);

            for (Points.ScoredPoint point : globalResults) {
                try {
                    long pointId = point.getId().getNum();
                    Integer productId = (int) pointId;

                    // Point ID가 0이면 payload에서 추출
                    if (pointId == 0) {
                        var payload = point.getPayloadMap();
                        if (!payload.isEmpty()) {
                            if (payload.containsKey("id") && payload.get("id").hasIntegerValue()) {
                                productId = (int) payload.get("id").getIntegerValue();
                            } else if (payload.containsKey("product_id") && payload.get("product_id").hasIntegerValue()) {
                                productId = (int) payload.get("product_id").getIntegerValue();
                            }
                        }
                    }

                    if (productId == 0) continue;  // ID가 0이면 스킵

                    Optional<Product> productOpt = productRepository.findById(productId);

                    if (productOpt.isPresent()) {
                        Product p = productOpt.get();
                        lastSimilarityScore = point.getScore();

                        if (lastSimilarityScore >= 0.25) {
                            log.info("전역 폴백 매칭 성공 '{}' → '{}' (type={}, 유사도={})",
                                    query, p.getName(), p.getType(), String.format("%.2f", lastSimilarityScore));
                            return Optional.of(p);
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid product ID in global search: {}", point.getId());
                }
            }

            log.warn("'{}' 전역 검색에서도 적절한 매칭을 찾지 못했습니다.", query);
            return Optional.empty();

        } catch (Exception e) {
            log.error("전역 폴백 검색 실패: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 쿼리 문자열을 임베딩 벡터로 변환 (재시도 포함)
     */
    private float[] embedQuery(String query) {
        int maxRetries = 3;
        long retryDelay = 1000; // 1초

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                EmbeddingResponse response = embeddingModel.embedForResponse(List.of(query.trim()));
                return response.getResults().get(0).getOutput();
            } catch (Exception e) {
                log.warn("임베딩 생성 실패 (시도 {}/{}): {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                        retryDelay *= 2; // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("임베딩 생성 중단됨", ie);
                    }
                } else {
                    log.error("임베딩 생성 최종 실패 - query: {}", query);
                    throw new RuntimeException("임베딩 생성 실패: " + e.getMessage(), e);
                }
            }
        }

        throw new RuntimeException("임베딩 생성 실패");
    }

    /**
     * Qdrant에서 벡터 검색 수행
     * @param queryVector 검색할 벡터
     * @param type 제품 타입 (null이면 전체 검색)
     * @param limit 검색 결과 제한 수
     * @return 검색 결과 리스트
     */
    private List<Points.ScoredPoint> searchVectors(float[] queryVector, String type, int limit) {
        try {
            // float[] → List<Float> 변환
            List<Float> vectorList = new ArrayList<>(queryVector.length);
            for (float value : queryVector) {
                vectorList.add(value);
            }

            Points.SearchPoints.Builder searchBuilder = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setVectorName("default")  // Named vector 지정
                    .addAllVector(vectorList)
                    .setLimit(limit)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build());

            // 타입 필터 추가 (있는 경우)
            if (type != null && !type.isBlank()) {
                Points.Filter filter = Points.Filter.newBuilder()
                        .addMust(Points.Condition.newBuilder()
                                .setField(Points.FieldCondition.newBuilder()
                                        .setKey("type")
                                        .setMatch(Points.Match.newBuilder()
                                                .setKeyword(type.toLowerCase())
                                                .build())
                                        .build())
                                .build())
                        .build();
                searchBuilder.setFilter(filter);
            }

            List<Points.ScoredPoint> results = qdrantClient.searchAsync(searchBuilder.build())
                    .get();

            log.debug("Qdrant 검색 완료: {} 결과 (type={})", results.size(), type);
            return results;

        } catch (ExecutionException | InterruptedException e) {
            log.error("Qdrant 벡터 검색 중 오류 발생: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }
}