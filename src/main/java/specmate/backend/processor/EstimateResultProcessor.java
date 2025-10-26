package specmate.backend.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.EstimateResult.Product;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class EstimateResultProcessor {

    private final ObjectMapper objectMapper;

    private static final List<String> SERIES_ORDER = List.of(
            "case", "cpu", "vga", "ram", "ssd", "power", "mainboard", "cooler", "hdd"
    );

    private static final Map<String, String> MAIN_KEY_NORMALIZER;
    static {
        Map<String, String> m = new HashMap<>();
        for (String k : List.of("case", "chassis", "tower")) m.put(k, "case");
        for (String k : List.of("cpu", "processor")) m.put(k, "cpu");
        for (String k : List.of("vga", "gpu", "graphics", "graphic_card", "video_card")) m.put(k, "vga");
        for (String k : List.of("ram", "memory", "ddr", "ddr4", "ddr5")) m.put(k, "ram");
        for (String k : List.of("ssd", "nvme", "m2", "m_2", "storage")) m.put(k, "ssd");
        for (String k : List.of("power", "psu", "power_supply", "smps")) m.put(k, "power");
        for (String k : List.of("mainboard", "motherboard", "mb")) m.put(k, "mainboard");
        for (String k : List.of("cooler", "cooling", "cpu_cooler")) m.put(k, "cooler");
        for (String k : List.of("hdd", "harddisk", "hard_drive")) m.put(k, "hdd");
        MAIN_KEY_NORMALIZER = m;
    }

    private static String normalizeType(String raw) {
        if (raw == null) return "unknown";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return MAIN_KEY_NORMALIZER.getOrDefault(s, s);
    }

    /** GPT 응답 → EstimateResult 파싱 */
    public EstimateResult parse(String gptMessage, Map<String, Product> fallbackMap) {
        try {
            log.info("GPT RAW MESSAGE: {}", gptMessage);

            String cleaned = extractJsonOnly(gptMessage);
            log.info("EXTRACTED JSON: {}", cleaned);

            if (cleaned == null || cleaned.isBlank() || "{}".equals(cleaned)) {
                log.debug("자연어 응답 감지 → 텍스트 응답으로 처리");
                EstimateResult result = new EstimateResult();
                result.setText(gptMessage);
                result.setProducts(List.of());
                return result;
            }

            JsonNode root = objectMapper.readTree(cleaned);
            EstimateResult result = new EstimateResult();

            result.setBuildName(getText(root, "build_name", "AI 기반 견적"));
            result.setBuildDescription(getText(root, "build_description", "RAG 데이터를 기반으로 구성된 자동 견적입니다."));

            // total 값 정규화 (쉼표, 원화기호 제거)
            String totalRaw = getText(root, "total", "0");
            result.setTotalPrice(totalRaw.replaceAll("[^0-9]", ""));

            result.setText(getText(root, "text", getText(root, "content", "")));

            // another_input_text
            if (root.has("another_input_text") && root.get("another_input_text").isArray()) {
                List<String> qList = new ArrayList<>();
                for (JsonNode q : root.get("another_input_text")) qList.add(q.asText());
                result.setAnotherInputText(qList);
            } else {
                result.setAnotherInputText(List.of());
            }

            Map<String, Product> pick = new LinkedHashMap<>();

            JsonNode listNode = root.has("components") ? root.get("components") :
                    root.has("products") ? root.get("products") : null;

            if (listNode != null && listNode.isArray()) {
                for (JsonNode node : listNode) {
                    Product p = new Product();
                    p.setId(getText(node, "id", null));

                    String type = getText(node, "type",
                            getText(node, "category",
                                    getText(node, "product_type", "unknown")));
                    p.setType(normalizeType(type));

                    String matched = getText(node, "matched_name", null);
                    p.setAiName(matched != null ? matched : getText(node, "ai_name", "미선택"));
                    p.setMatchedName(matched);
                    p.setDescription(getText(node, "description", "RAG 데이터 없음"));

                    long price = parseLong(stripWon(getText(node, "price", "0")));
                    price = Math.max(0, Math.min(price, 3_500_000));
                    p.setPrice(String.valueOf(price));

                    String image = getText(node, "image",
                            getText(node, "image_url",
                                    getText(node, "img", "")));
                    p.setImage(image);

                    pick.put(p.getType(), p);
                }
            }

            if (pick.isEmpty()) {
                log.warn("⚠️ GPT 응답 내 components 비어 있음 → JSON 구조 확인 필요");
            }

            // fallback 병합
            if (fallbackMap != null && !fallbackMap.isEmpty()) {
                for (var entry : fallbackMap.entrySet()) {
                    String type = normalizeType(entry.getKey());
                    Product fb = entry.getValue();
                    Product exist = pick.get(type);
                    if (exist == null) pick.put(type, fb);
                    else if ("미선택".equalsIgnoreCase(exist.getMatchedName()) && fb.getMatchedName() != null)
                        exist.setMatchedName(fb.getMatchedName());
                }
            }

            List<Product> finalList = new ArrayList<>();
            for (String cat : SERIES_ORDER) {
                Product exist = pick.get(cat);
                finalList.add(exist != null ? exist : defaultProduct(cat));
            }

            if (finalList.stream().allMatch(p -> "0".equals(p.getPrice()))) {
                log.warn("모든 부품의 가격이 0으로 파싱됨 → 프롬프트/JSON 구조 점검 필요");
            }

            result.setProducts(finalList);

            long total = finalList.stream()
                    .mapToLong(p -> parseLong(stripWon(p.getPrice())))
                    .sum();
            if (total > 3_500_000) total = 3_500_000;
            result.setTotalPrice(Long.toString(total));

            log.info("EstimateResult 파싱 완료: {} ({}개 부품, 총합 {}원)",
                    result.getBuildName(), finalList.size(), result.getTotalPrice());
            return result;

        } catch (Exception e) {
            log.error("GPT 응답 파싱 실패: {}", e.getMessage());
            EstimateResult fallback = new EstimateResult();
            fallback.setBuildName("AI 견적");
            fallback.setTotalPrice("0");
            fallback.setProducts(List.of());
            fallback.setAnotherInputText(List.of());
            return fallback;
        }
    }

    /** JSON만 추출 (```json 등 제거 포함) */
    private String extractJsonOnly(String s) {
        if (s == null) return "{}";
        String t = s.trim();

        //  마크다운 코드블록 제거
        t = t.replaceAll("(?s)```(json)?", "").trim();

        // GPT가 문자열로 감싼 JSON 처리
        if (t.startsWith("\"{") && t.endsWith("}\"")) {
            t = t.substring(1, t.length() - 1).replace("\\\"", "\"");
        }

        // 본문 텍스트와 JSON 섞인 경우 첫 {~마지막 } 추출
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) {
            String jsonPart = t.substring(first, last + 1).trim();
            if (!jsonPart.startsWith("{")) jsonPart = "{" + jsonPart;
            if (!jsonPart.endsWith("}")) jsonPart = jsonPart + "}";
            return jsonPart;
        }

        return "{}";
    }

    private String getText(JsonNode node, String key, String defaultVal) {
        if (node != null && node.has(key) && !node.get(key).isNull()) {
            JsonNode valNode = node.get(key);
            return valNode.isTextual() ? valNode.asText() : valNode.toString();
        }
        return defaultVal;
    }

    private String stripWon(String s) { return s.replaceAll("[^0-9]", ""); }
    private long parseLong(String s) { try { return Long.parseLong(s == null || s.isBlank() ? "0" : s); } catch (Exception e) { return 0; } }
    private String safe(Object o) { return o == null ? "" : o.toString().trim(); }

    private Product defaultProduct(String type) {
        Product p = new Product();
        p.setType(type);
        p.setMatchedName("미선택");
        p.setDescription("RAG 데이터 없음");
        p.setPrice("0");
        p.setImage("");
        return p;
    }
}
