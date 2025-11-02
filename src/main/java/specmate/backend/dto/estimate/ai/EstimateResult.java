package specmate.backend.dto.estimate.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstimateResult {

    @JsonProperty("ai_estimate_id")
    private String aiEstimateId;

    @JsonProperty("build_name")
    private String buildName;

    @JsonProperty("build_description")
    private String buildDescription;

    @JsonProperty("total")
    private Integer totalPrice;

    private String notes;
    private String text;

    @JsonProperty("another_input_text")
    private List<String> anotherInputText;

    @JsonProperty("components")
    @JsonAlias({"products"})
    private List<Product> products;

    @JsonIgnore
    private String selectType;

    public boolean isEmpty() {
        return products == null || products.isEmpty();
    }

    public boolean isAllDefaults() {
        if (products == null || products.isEmpty()) return true;

        long validCount = products.stream()
                .filter(p -> {
                    if (p.getDetail() == null) return false;
                    return parsePrice(p.getDetail().getPrice()) > 0;
                })
                .count();

        return validCount == 0;
    }

    private int parsePrice(String priceStr) {
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Product {

        @JsonProperty("id")
        @JsonAlias({"product_id"})
        private String id;

        @JsonAlias({"category", "type"})
        private String type;

<<<<<<< HEAD
        @JsonProperty("name")
        @JsonAlias({"matched_name"})
        private String name;
=======
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String aiName; // GPT 생성 이름
>>>>>>> develop

        private String description;

        @JsonProperty("detail")
        private Detail detail;

        @JsonProperty("ai_name")
        @JsonAlias({"aiName"})
        private String aiName;

        @JsonIgnore
        private Integer productId;

        @JsonIgnore
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Detail {
        private String price;
        private String image;
    }
}
