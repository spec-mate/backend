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
    private final ProductRepository productRepository;


    public Optional<ProductEmbedding> getCheapestByType(String type) {
        return embeddingRepository.findCheapestByType(type);
    }



    /** 카테고리별 의미 기반 검색 (fallback 포함) */
    @Transactional(readOnly = true)
    public List<Product> searchSimilarProductsByCategory(String query, int limitPerCategory) {

        // GPT 출력 규칙에 맞춘 표준 카테고리 순서
        List<String> gptTypes = List.of(
                "case", "cpu", "vga", "RAM", "power",
                "ssd", "mainboard", "cooler", "hdd"
        );

        List<Product> allResults = new ArrayList<>();

        try {
            // 사용자 입력 → 벡터 임베딩
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(query));
            float[] queryVector = response.getResults().get(0).getOutput();
            String queryVectorString = toPgVectorString(queryVector);

            // 각 카테고리별 검색 수행
            for (String gptType : gptTypes) {
                List<String> aliases = mapGptTypeToNormalizedAliases(gptType); // 요청한 별칭 기준
                List<Product> resultsForType = new ArrayList<>();

                // (a) 정규화 타입 별칭들로 탐색
                for (String alias : aliases) {
                    List<ProductEmbedding> found = embeddingRepository
                            .findNearestEmbeddingsByType(queryVectorString, alias, limitPerCategory);
                    // ↑ 네 기존 레포지토리에선 p.type ILIKE 기반이라면, normalized_type 기반 메서드로 바꿨다면:
                    // .findNearestByNormalizedType(queryVectorString, alias, limitPerCategory);

                    resultsForType.addAll(
                            found.stream()
                                    .map(ProductEmbedding::getProduct)
                                    .filter(this::isValidProduct)
                                    .toList()
                    );
                }

                // (b) 결과 없으면 전역 근접 검색으로 폴백(타입 무관)
                if (resultsForType.isEmpty()) {
                    log.warn("[{}] 검색 결과 없음 → 전역 근접 검색 폴백 시도", gptType);
                    List<ProductEmbedding> fb = embeddingRepository
                            .findNearestEmbeddings(queryVectorString, Math.max(1, limitPerCategory));
                    resultsForType.addAll(
                            fb.stream()
                                    .map(ProductEmbedding::getProduct)
                                    .filter(this::isValidProduct)
                                    .toList()
                    );
                }

                // (c) 최종 결과 반영 (동일 제품명 dedupe)
                if (!resultsForType.isEmpty()) {
                    List<Product> distinct = resultsForType.stream()
                            .filter(Objects::nonNull)
                            .filter(p -> p.getName() != null)
                            .collect(Collectors.collectingAndThen(
                                    Collectors.toMap(Product::getName, p -> p, (a, b) -> a, LinkedHashMap::new),
                                    m -> new ArrayList<>(m.values())
                            ));
                    allResults.addAll(distinct);
                    log.info("[{}] 검색 완료: {}개", gptType, distinct.size());
                } else {
                    log.error("[{}] 폴백도 실패 → 해당 타입 후보 없음", gptType);
                }
            }

        } catch (Exception e) {
            log.error("카테고리별 검색 실패: {}", e.getMessage(), e);
        }

        return allResults;
    }

    /** gptType → normalized_type 별칭 목록 매핑 */
    private static List<String> mapGptTypeToNormalizedAliases(String gptType) {
        if (gptType == null) return List.of();
        String t = gptType.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "cpu", "processor" -> List.of("cpu");
            case "gpu", "graphics", "graphic_card", "vga", "video_card" -> List.of("vga", "gpu");
            case "ram", "memory", "ram_memory", "dimm", "ddr", "ddr4", "ddr5" -> List.of("ram");
            case "ssd", "nvme", "m2", "m_2", "solid_state_drive", "storage" -> List.of("ssd", "storage");
            case "hdd", "harddisk", "hard_drive" -> List.of("hdd");
            case "psu", "power", "power_supply", "smps" -> List.of("power", "psu");
            case "mainboard", "motherboard", "mb" -> List.of("mainboard", "motherboard");
            case "cooler", "cooling", "cpu_cooler" -> List.of("cooler");
            case "case", "chassis", "tower" -> List.of("case");
            default -> List.of(t);
        };
    }

    /** 유효한 제품인지 판별 */
    private boolean isValidProduct(Product p) {
        if (p == null || p.getLowestPrice() == null) return false;
        Object priceObj = p.getLowestPrice().get("price");
        if (priceObj == null) return false;

        try {
            int price = Integer.parseInt(priceObj.toString().replaceAll("[^0-9]", ""));
            return price >= 10_000 && price <= 3_000_000;
        } catch (Exception e) {
            return false;
        }
    }

    /** float[] → PostgreSQL pgvector 문자열 변환 */
    private String toPgVectorString(float[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
