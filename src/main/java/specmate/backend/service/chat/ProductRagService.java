package specmate.backend.service.chat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.Product;
import specmate.backend.service.product.ProductSearchService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductRagService {

    private final ProductSearchService productSearchService;
    private static final int SEARCH_LIMIT_PER_CATEGORY = 10;

    /** 사용자 입력 기반 RAG 컨텍스트 (GPT 호출 전) 사용자의 자연어 입력(userInput)을 기반으로 유사 제품 검색 GPT에게 전달할 컨텍스트를 구성 */
    public RagContext buildRagContext(String userInput) {
        List<Product> products = productSearchService.searchSimilarProductsByCategory(userInput, SEARCH_LIMIT_PER_CATEGORY);

        // 컨텍스트 텍스트 구성
        String componentText = products.stream()
                .map(p -> String.format("[%s] %s - %s원 (%s)",
                        safe(p.getType()),
                        safe(p.getName()),
                        extractPrice(p),
                        safe(p.getManufacturer())))
                .collect(Collectors.joining("\n"));

        // Fallback DTO 맵 구성
        Map<String, EstimateResult.Product> dtoFallbackMap = products.stream()
                .filter(p -> p.getType() != null)
                .collect(Collectors.toMap(
                        p -> p.getType().toLowerCase(Locale.ROOT),
                        this::toEstimateProductDto,
                        (a, b) -> a
                ));

        return new RagContext(componentText, dtoFallbackMap);
    }

    /** GPT가 생성한 EstimateResult 기반 RAG 컨텍스트 (후처리 매칭) GPT의 ai_name / description을 기반으로 DB 제품 매칭 중복된 matched_name은 1회만 사용 */
    public RagContext buildRagContext(EstimateResult estimateResult) {
        Map<String, EstimateResult.Product> dtoFallbackMap = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        Set<String> usedMatchedNames = new HashSet<>();

        for (EstimateResult.Product comp : estimateResult.getProducts()) {
            // 검색용 query 생성 (aiName 우선, 없으면 description)
            String query = comp.getAiName();
            if (query == null || query.isBlank()) {
                query = comp.getDescription();
            }

            // 카테고리 기반 유사도 검색
            List<Product> products = productSearchService.searchSimilarProductsByCategory(query, SEARCH_LIMIT_PER_CATEGORY);

            // 중복 matched_name 방지
            Product top = null;
            for (Product p : products) {
                if (!usedMatchedNames.contains(p.getName())) {
                    top = p;
                    usedMatchedNames.add(p.getName());
                    break;
                }
            }

            EstimateResult.Product matchedDto;
            if (top != null) {
                matchedDto = toEstimateProductDto(top);
            } else {
                matchedDto = EstimateResult.Product.builder()
                        .type(comp.getType())
                        .matchedName("미선택")
                        .price("0")
                        .description(comp.getDescription())
                        .build();
            }

            // ai_name도 그대로 보존
            matchedDto.setAiName(comp.getAiName());

            dtoFallbackMap.put(comp.getType().toLowerCase(Locale.ROOT), matchedDto);

            // GPT 컨텍스트용 텍스트 누적
            sb.append(String.format("[%s] %s ↔ %s - %s원 (%s)\n",
                    safe(matchedDto.getType()),
                    safe(matchedDto.getAiName()),
                    safe(matchedDto.getMatchedName()),
                    safe(matchedDto.getPrice()),
                    safe(matchedDto.getDescription())));
        }

        return new RagContext(sb.toString().trim(), dtoFallbackMap);
    }

    /** Product → EstimateResult.Product 변환 */
    private EstimateResult.Product toEstimateProductDto(Product p) {
        return EstimateResult.Product.builder()
                .id(String.valueOf(p.getId()))
                .type(safe(p.getType()))
                .matchedName(safe(p.getName()))
                .price(extractPrice(p))
                .description(safe(p.getManufacturer()))
                .build();
    }

    /** DB의 lowest_price → price 변환 */
    private String extractPrice(Product p) {
        try {
            if (p.getLowestPrice() != null && p.getLowestPrice().get("price") != null) {
                Object price = p.getLowestPrice().get("price");
                return String.valueOf(price).replaceAll("[^0-9]", "");
            }
        } catch (Exception ignored) {}
        return "0";
    }

    /** null-safe 헬퍼 */
    private String safe(String value) {
        return value != null ? value : "";
    }

    @Getter
    @RequiredArgsConstructor
    public static class RagContext {
        private final String componentText;
        private final Map<String, EstimateResult.Product> dtoFallbackMap;

        public String getInstructions() {
            return componentText;
        }
    }
}
