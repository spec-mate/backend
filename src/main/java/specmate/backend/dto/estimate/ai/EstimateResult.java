package specmate.backend.dto.estimate.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimateResult {

    @JsonProperty("ai_estimate_id")
    private String aiEstimateId;

    @JsonProperty("build_name")
    private String buildName;

    @JsonProperty("build_description")
    private String buildDescription;

    @JsonProperty("total")
    private String totalPrice;

    private String notes;
    private String text;

    @JsonProperty("another_input_text")
    private List<String> anotherInputText;

    @JsonProperty("components")
    @JsonAlias({"products", "items"})
    private List<Product> products;

    public boolean isEmpty() {
        return products == null || products.isEmpty();
    }

    /** 모든 제품이 미선택 또는 가격 0이면 true → 비견적성 응답 */
    public boolean isAllDefaults() {
        if (products == null || products.isEmpty()) return true;

        return products.stream().noneMatch(p -> {
            String name = p.getMatchedName();
            int price = parsePrice(p.getPrice());

            return name != null && !name.isBlank()
                    && !name.equals("미선택")
                    && !name.equalsIgnoreCase("none")
                    && price > 0;
        });
    }

    private int parsePrice(String priceStr) {
        if (priceStr == null) return 0;
        try {
            String cleaned = priceStr.replaceAll("[^0-9]", "");
            if (cleaned.isBlank()) return 0;
            return Integer.parseInt(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Product {

        @JsonProperty("id")
        @JsonAlias({"product_id"})
        private String id;

        @JsonAlias({"category", "type"})
        private String type;

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String aiName; // GPT 생성 이름

        @JsonProperty("matched_name")
        private String matchedName; // 실제 선택된 제품명

        private String price;
        private String image;
        private String description;
    }
}
