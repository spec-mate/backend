package specmate.backend.dto.estimate.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagContext {

    private String ragJson;
    private Map<String, EstimateResult.Product> dtoFallbackMap;

    public String getJson() {
        return ragJson != null ? ragJson : "[]";
    }
}
