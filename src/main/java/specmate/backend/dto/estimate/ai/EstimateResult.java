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

    @JsonProperty("another_input_text")
    private List<String> anotherInputText;

    @JsonProperty("components")     // JSON 내려갈 땐 components
    @JsonAlias({"products"})        // JSON 들어올 땐 products도 허용
    private List<Product> products;

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
