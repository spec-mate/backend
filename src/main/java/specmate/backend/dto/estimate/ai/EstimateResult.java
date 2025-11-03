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
    @JsonAlias({"products"})
    private List<Product> products;

    public boolean isEmpty() {
        return products == null || products.isEmpty();
    }

    public boolean isAllDefaults() {
        if (products == null || products.isEmpty()) return true;

        return products.stream().allMatch(p ->
                (p.getMatchedName() == null || p.getMatchedName().isBlank() ||
                        p.getMatchedName().equals("미선택") || p.getMatchedName().equalsIgnoreCase("none")) &&
                        (p.getPrice() == null || p.getPrice().isBlank() || parsePrice(p.getPrice()) == 0)
        );
    }

    private int parsePrice(String priceStr) {
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
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
        private String matchedName; // DB 매칭 후 실제 이름

        private String price;
        private String description;
        private String image; // 제품 이미지 URL
    }
}
