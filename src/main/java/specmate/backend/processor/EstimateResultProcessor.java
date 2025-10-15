package specmate.backend.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import specmate.backend.dto.aiestimate.EstimateResult;
import specmate.backend.dto.aiestimate.EstimateResult.Product;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EstimateResultProcessor {

    private final ObjectMapper objectMapper;

    public EstimateResult parse(String gptMessage) {
        try {
            // GPT 응답 문자열 정리
            String cleaned = gptMessage
                    .replaceAll("(?s)```json", "")
                    .replaceAll("(?s)```", "")
                    .replaceAll("(?s)<[^>]*>", "")
                    .trim();

            cleaned = cleaned
                    .replaceAll("(?<=\\d)_(?=\\d)", "")
                    .replaceAll("([0-9]),([0-9])", "$1$2")
                    .replaceAll("([0-9])원", "$1");

            // JSON 파싱
            JsonNode root = objectMapper.readTree(cleaned);
            EstimateResult result = new EstimateResult();

            result.setBuildName(getText(root, "build_name", "AI 견적"));
            result.setBuildDescription(getText(root, "note"));
            result.setTotalPrice(getText(root, "total", "0"));
            result.setNotes(getText(root, "intro"));

            // another_input_text 처리
            if (root.has("another_input_text") && root.get("another_input_text").isArray()) {
                List<String> qList = new ArrayList<>();
                for (JsonNode q : root.get("another_input_text")) {
                    qList.add(q.asText());
                }
                result.setAnotherInputText(qList);
            } else {
                result.setAnotherInputText(List.of());
            }

            // components / products / main 처리
            List<Product> productList = new ArrayList<>();

            // (1) main 객체 형태인 경우
            if (root.has("main") && root.get("main").isObject()) {
                JsonNode main = root.get("main");
                main.fieldNames().forEachRemaining(category -> {
                    JsonNode item = main.get(category);
                    Product p = new Product();
                    p.setType(category);
                    p.setName(getText(item, "name", "미선택"));
                    p.setDescription(getText(item, "description", "정보 없음"));
                    p.setPrice(getText(item, "price", "0"));
                    p.setSimilarity(null);
                    productList.add(p);
                });
            }

            // (2) components 또는 products 배열 형태인 경우
            else if (root.has("components") && root.get("components").isArray()) {
                for (JsonNode node : root.get("components")) {
                    productList.add(objectMapper.treeToValue(node, Product.class));
                }
            } else if (root.has("products") && root.get("products").isArray()) {
                for (JsonNode node : root.get("products")) {
                    productList.add(objectMapper.treeToValue(node, Product.class));
                }
            }

            // 기본값 보정 (비어 있으면 기본 부품 9개 생성)
            if (productList.isEmpty()) {
                String[] categories = {"case", "cpu", "gpu", "ram", "storage", "psu", "motherboard", "cooler", "hdd"};
                for (String cat : categories) {
                    Product p = new Product();
                    p.setType(cat);
                    p.setName("미선택");
                    p.setDescription("선택된 부품 없음");
                    p.setPrice("0");
                    productList.add(p);
                }
            }

            result.setProducts(productList);
            log.info("EstimateResult 파싱 완료: {} ({}개 부품)", result.getBuildName(), productList.size());
            return result;

        } catch (Exception e) {
            log.error("GPT 응답 파싱 실패: {}", e.getMessage());
            EstimateResult fallback = new EstimateResult();
            fallback.setBuildName("AI 견적");
            fallback.setTotalPrice("0");
            fallback.setProducts(new ArrayList<>());
            fallback.setAnotherInputText(List.of());
            return fallback;
        }
    }

    /** 안전한 텍스트 추출 */
    private String getText(JsonNode node, String key, String defaultVal) {
        if (node != null && node.has(key) && !node.get(key).isNull()) {
            return node.get(key).asText();
        }
        return defaultVal;
    }

    private String getText(JsonNode node, String key) {
        return getText(node, key, "");
    }
}
