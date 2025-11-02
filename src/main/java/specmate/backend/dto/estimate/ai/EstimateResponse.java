package specmate.backend.dto.estimate.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.EstimateProduct;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstimateResponse {

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
    private List<ComponentResponse> components;

    public static EstimateResponse fromEntityWithProducts(AiEstimate estimate, List<EstimateProduct> products) {
        return EstimateResponse.builder()
                .aiEstimateId(estimate.getId().toString())
                .buildName(estimate.getTitle())
                .buildDescription(estimate.getDescription())
                .totalPrice(estimate.getTotalPrice())
                .notes(estimate.getDescription())
                .components(products.stream()
                        .map(ep -> ComponentResponse.builder()
                                .type(ep.getType())
                                .name(ep.getMatchedName())
                                .description(ep.getDescription())
                                .detail(ComponentResponse.Detail.builder()
                                        .price(String.valueOf(ep.getUnitPrice()))
                                        .image(ep.getImage())
                                        .build())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentResponse {

        @JsonAlias({"category", "type"})
        private String type;

        @JsonProperty("name")
        @JsonAlias({"matched_name"})
        private String name;

        private String description;

        @JsonProperty("detail")
        private Detail detail;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Detail {
            private String price;
            private String image;
        }
    }
}
