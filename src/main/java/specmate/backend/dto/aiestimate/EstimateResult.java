package specmate.backend.dto.aiestimate;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonProperty("components")
    private List<Product> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        private String id;
        @JsonAlias({"category", "type"})
        private String type;
        private String name;
        private String price;
        private String description;
        private Float similarity;
    }
}
