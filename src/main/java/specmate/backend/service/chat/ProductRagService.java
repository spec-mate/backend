package specmate.backend.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.Getter;
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
    private static final int SEARCH_LIMIT_PER_CATEGORY = 5;

    public RagContext buildRagContext(String query) {
        List<Product> products = productSearchService.searchSimilarProductsByCategory(query, SEARCH_LIMIT_PER_CATEGORY);
        String componentText = buildComponentText(products);

        Map<String, EstimateResult.Product> dtoFallbackMap = products.stream()
                .filter(p -> p.getType() != null)
                .collect(Collectors.toMap(
                        p -> p.getType().toLowerCase(Locale.ROOT),
                        p -> {
                            EstimateResult.Product dto = new EstimateResult.Product();
                            dto.setId(p.getId() != null ? p.getId().toString() : "");
                            dto.setType(p.getType());
                            dto.setName(p.getName());
                            dto.setPrice(
                                    p.getLowestPrice() != null
                                            ? p.getLowestPrice().getOrDefault("price", "0").toString()
                                            : "0"
                            );
                            return dto;
                        },
                        (a, b) -> a
                ));

        String instructions = componentText.isBlank()
                ? "[검색된 후보 부품 없음]"
                : "[검색된 후보 부품 목록]\n" + componentText;

        return new RagContext(products, dtoFallbackMap, instructions);
    }

    private String buildComponentText(List<Product> products) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            String price = p.getLowestPrice() != null
                    ? p.getLowestPrice().getOrDefault("price", "0").toString()
                    : "0";
            sb.append(String.format(
                    "  { \"id\": \"%s\", \"type\": \"%s\", \"name\": \"%s\", \"price\": \"%s\" }",
                    p.getId() != null ? p.getId().toString() : "",
                    escapeJson(p.getType()),
                    escapeJson(p.getName()),
                    price
            ));
            if (i < products.size() - 1) sb.append(",\n");
        }
        sb.append("\n]");
        return sb.toString();
    }

    /** 따옴표나 개행이 포함된 문자열을 안전하게 escape 처리 */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    @Getter
    public static class RagContext {
        private final List<Product> products;
        private final Map<String, EstimateResult.Product> dtoFallbackMap;
        private final String instructions;

        public RagContext(List<Product> products,
                          Map<String, EstimateResult.Product> dtoFallbackMap,
                          String instructions) {
            this.products = products;
            this.dtoFallbackMap = dtoFallbackMap;
            this.instructions = instructions;
        }
    }
}
