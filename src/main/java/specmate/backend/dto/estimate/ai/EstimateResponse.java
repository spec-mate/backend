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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstimateResponse {

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
    private List<ComponentResponse> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentResponse {

        @JsonAlias({"category", "type"})
        private String type;

        @JsonProperty("matched_name")
        private String matchedName;

        private String price;
        private String image;
        private String description;
    }
}
