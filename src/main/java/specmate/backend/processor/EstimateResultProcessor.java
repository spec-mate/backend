package specmate.backend.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import specmate.backend.dto.aiestimate.EstimateResult;

@Component
@RequiredArgsConstructor
public class EstimateResultProcessor {

    private final ObjectMapper objectMapper;

    public EstimateResult parse(String gptMessage) {
        try {
            String cleaned = gptMessage
                    .replaceAll("(?s)```json", "")
                    .replaceAll("(?s)```", "")
                    .trim();

            cleaned = cleaned.replaceAll("(?<=\\d)_(?=\\d)", "");
            cleaned = cleaned.replaceAll("([0-9]),([0-9])", "$1$2")
                    .replaceAll("([0-9])원", "$1");

            EstimateResult result = objectMapper.readValue(cleaned, EstimateResult.class);

            if (result.getBuildName() == null || result.getBuildName().isBlank()) {
                result.setBuildName("AI 견적");
            }
            if (result.getTotalPrice() == null || result.getTotalPrice().isBlank()) {
                result.setTotalPrice("0");
            }
            if (result.getProducts() == null) {
                result.setProducts(new java.util.ArrayList<>());
            }

            return result;

        } catch (Exception e) {
            EstimateResult fallback = new EstimateResult();
            fallback.setBuildName("AI 견적");
            fallback.setTotalPrice("0");
            fallback.setProducts(new java.util.ArrayList<>());
            return fallback;
        }
    }


}