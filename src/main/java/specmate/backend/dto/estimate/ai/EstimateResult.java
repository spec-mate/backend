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

    @JsonProperty("components")     // JSON 내려갈 땐 components
    @JsonAlias({"products"})        // JSON 들어올 땐 products도 허용
    private List<Product> products;


    /** 상태 플래그 */
    private boolean empty;
    private boolean allDefaults;

    /** 구성품이 비어 있는지 확인 */
    public boolean isEmpty() {
        return products == null || products.isEmpty();
    }

    /** 모든 구성품이 '미선택'이거나 가격이 0, null, 혹은 빈 문자열인 경우 */
    public boolean isAllDefaults() {
        if (products == null || products.isEmpty()) return true;

        return products.stream().allMatch(p ->
                (p.getName() == null || p.getName().isBlank() || p.getName().equals("미선택") || p.getName().equalsIgnoreCase("none")) &&
                        (p.getPrice() == null || p.getPrice().isBlank() || parsePrice(p.getPrice()) == 0)
        );
    }

    /** 문자열 가격을 안전하게 정수로 변환 (비정상 입력은 0 처리) */
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
    @JsonInclude(JsonInclude.Include.NON_NULL) // null 필드는 직렬화 시 제외
    public static class Product {

        @JsonProperty("id")
        @JsonAlias({"product_id"})
        private String id;

        @JsonAlias({"category", "type"})
        private String type;
        private String name;
        private String price;
        private String description;
    }
}
