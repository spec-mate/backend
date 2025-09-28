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

            return objectMapper.readValue(cleaned, EstimateResult.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("GPT 응답 전처리에 실패했습니다. (파싱 오류 확인 필요)", e);
        }
    }
}