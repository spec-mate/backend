package specmate.backend.dto.aiestimate;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private List<Component> components;
    private String total;
    private String notes;

    @JsonProperty("another_input_text")
    private List<String> anotherInputText;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Component {
        @JsonAlias ({"category", "type"})
        private String type;
        private String name;
        private String price;
        private String description;
    }
}
