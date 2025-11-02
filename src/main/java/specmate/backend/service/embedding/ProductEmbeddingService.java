package specmate.backend.service.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.RagContext;
import specmate.backend.entity.Product;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.product.ProductRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEmbeddingService {

    private final QdrantVectorStore qdrantVectorStore;
    private final EmbeddingModel embeddingModel;
    private final EstimateResultProcessor estimateResultProcessor;

    /** Qdrant에서 제품명 유사 검색 */
    public List<Document> searchSimilarProducts(String query, int topK) {
        try {
            if (query == null || query.isBlank()) {
                log.warn("검색어가 비어 있습니다. query={}", query);
                return List.of();
            }

            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build();

            List<Document> results = qdrantVectorStore.similaritySearch(request);
            log.info("'{}' 에 대한 유사도 검색 결과 {}개 반환", query, results.size());
            return results;

        } catch (Exception e) {
            log.error("Qdrant 검색 중 오류 발생 (query={}): {}", query, e.getMessage(), e);
            return List.of();
        }
    }

    /** 결과를 간단히 텍스트 리스트로 변환 (API용) */
    public List<String> searchProductNames(String query, int topK) {
        return searchSimilarProducts(query, topK)
                .stream()
                .map(d -> (String) d.getMetadata().get("name"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * AI 응답(EstimateResult) 기반으로 실제 제품 매칭 수행
     * Qdrant에서 유사한 제품을 찾아 EstimateResult.Product 형태로 반환
     */
    public List<EstimateResult.Product> matchAiResponseToProducts(EstimateResult result) {
        if (result == null || result.getProducts() == null) {
            log.warn("EstimateResult가 비어 있습니다.");
            return List.of();
        }

        List<EstimateResult.Product> matchedProducts = new ArrayList<>();

        for (EstimateResult.Product product : result.getProducts()) {
            String queryText = Optional.ofNullable(product.getAiName())
                    .filter(s -> !s.isBlank())
                    .orElse(Optional.ofNullable(product.getName()).orElse(""));

            if (queryText.isBlank()) {
                log.debug("빈 제품명으로 검색 불가: type={}", product.getType());
                continue;
            }

            try {
                SearchRequest request = SearchRequest.builder()
                        .query(queryText)
                        .topK(3)
                        .build();

                List<Document> docs = qdrantVectorStore.similaritySearch(request);

                if (docs.isEmpty()) {
                    log.warn("Qdrant 결과 없음: {}", queryText);
                    matchedProducts.add(EstimateResult.Product.builder()
                            .type(product.getType())
                            .name("미선택")
                            .description("유사 제품 없음")
                            .detail(new EstimateResult.Detail("0", ""))
                            .build());
                    continue;
                }

                // 가장 유사한 문서 선택
                Document topDoc = docs.get(0);
                Map<String, Object> meta = topDoc.getMetadata();

                String name = Objects.toString(meta.get("name"), "미선택");
                String price = String.valueOf(meta.getOrDefault("price", "0"));
                String image = Objects.toString(meta.get("image"), "");
                String type = Objects.toString(meta.getOrDefault("type", product.getType()));

                Object pid = meta.get("product_id");
                Integer productId = null;
                if (pid != null) {
                    try {
                        if (pid instanceof Number n) {
                            productId = n.intValue();
                        } else if (pid instanceof String s && !s.isBlank()) {
                            productId = Integer.parseInt(s);
                        }
                    } catch (Exception e) {
                        log.warn("product_id 파싱 실패: {}", pid);
                    }
                }

                EstimateResult.Product matched = EstimateResult.Product.builder()
                        .type(type)
                        .name(name)
                        .description(product.getDescription())
                        .detail(new EstimateResult.Detail(price, image))
                        .aiName(product.getAiName())
                        .productId(productId)
                        .build();

                matchedProducts.add(matched);
                log.debug("매칭 완료 [{}] → [{}] (product_id={})", queryText, name, productId);

            } catch (Exception e) {
                log.error("Qdrant 매칭 중 오류: {}", queryText, e);
            }
        }

        log.info("총 {}개 제품 매칭 완료", matchedProducts.size());
        return matchedProducts;
    }

    /** GPT 응답 처리 및 Qdrant 매칭 통합 */
    public EstimateResult processAiReply(String reply, RagContext ragContext) {
        if (reply == null || reply.isBlank()) {
            log.warn("[STEP5] AI 응답이 비어 있습니다.");
            return new EstimateResult();
        }

        try {
            EstimateResult parsed = estimateResultProcessor.parse(
                    reply,
                    ragContext != null ? ragContext.getDtoFallbackMap() : Map.of()
            );

            parsed.setProducts(matchAiResponseToProducts(parsed));

            log.info("[STEP5] processAiReply 완료: {}개 제품 매칭",
                    parsed.getProducts() != null ? parsed.getProducts().size() : 0);
            return parsed;

        } catch (Exception e) {
            log.error("[STEP5] processAiReply 중 오류 발생", e);
            return new EstimateResult();
        }
    }

    @Autowired
    private ProductRepository productRepository;

    public Product findProductById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID가 null입니다.");
        }

        int intId = id.intValue();

        return productRepository.findById(intId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Product ID: " + id));
    }
}
