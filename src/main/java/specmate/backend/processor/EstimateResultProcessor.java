package specmate.backend.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.EstimateResult.Product;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EstimateResultProcessor {

    private final ObjectMapper objectMapper;

    private static final List<String> SERIES_ORDER = List.of(
            "case", "cpu", "vga", "RAM", "ssd", "power", "mainboard", "cooler", "hdd"
    );

    private static final Map<String, String> MAIN_KEY_NORMALIZER;
    static {
        Map<String, String> m = new HashMap<>();
        for (String k : List.of("case", "chassis", "tower")) m.put(k, "case");
        for (String k : List.of("cpu", "processor")) m.put(k, "cpu");
        for (String k : List.of("vga","gpu","graphics","graphic_card","video_card")) m.put(k, "vga");
        for (String k : List.of("ram","RAM","ram_memory","memory","dimm","ddr","ddr4","ddr5")) m.put(k, "RAM");
        for (String k : List.of("ssd","nvme","m2","m_2","solid_state_drive","storage")) m.put(k, "ssd");
        for (String k : List.of("power","psu","power_supply","smps")) m.put(k, "power");
        for (String k : List.of("mainboard","motherboard","mb")) m.put(k, "mainboard");
        for (String k : List.of("cooler","cooling","cpu_cooler")) m.put(k, "cooler");
        for (String k : List.of("hdd","harddisk","hard_drive")) m.put(k, "hdd");
        MAIN_KEY_NORMALIZER = m;
    }

    private static String normalizeType(String raw) {
        if (raw == null) return "unknown";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return MAIN_KEY_NORMALIZER.getOrDefault(s, s);
    }

    public EstimateResult parse(String gptMessage) {
        return parse(gptMessage, Collections.emptyMap());
    }

    public EstimateResult parse(String gptMessage, Map<String, Product> fallbackMap) {
        try {
            String cleaned = extractJsonOnly(gptMessage);
            EstimateResult result = new EstimateResult();

            if ("{}".equals(cleaned.trim())) {
                log.info("EstimateResultProcessor JSON 구조가 감지되지 않음 → 비견적성 응답으로 처리");
                result.setText(gptMessage.trim());
                result.setProducts(List.of());
                result.setBuildName("");
                result.setTotalPrice("0");
                return result;
            }

            JsonNode root = objectMapper.readTree(cleaned);

            // 기본 메타 필드
            result.setText(getText(root, "text", ""));
            result.setBuildName(getText(root, "build_name", ""));
            result.setBuildDescription(getText(root, "build_description"));
            result.setTotalPrice(getText(root, "total", "0"));
            result.setNotes(getText(root, "notes"));

            // 후속 질문 리스트
            if (root.has("another_input_text") && root.get("another_input_text").isArray()) {
                List<String> qList = new ArrayList<>();
                for (JsonNode q : root.get("another_input_text")) qList.add(q.asText());
                result.setAnotherInputText(qList);
            } else {
                result.setAnotherInputText(List.of());
            }

            Map<String, Product> pick = new LinkedHashMap<>();

            // main 필드 처리
            if (root.has("main") && root.get("main").isObject()) {
                JsonNode main = root.get("main");
                Iterator<String> it = main.fieldNames();
                while (it.hasNext()) {
                    String key = it.next();
                    JsonNode item = main.get(key);
                    Product p = new Product();
                    p.setType(normalizeType(key));
                    p.setName(getText(item, "name", "미선택"));
                    p.setDescription(getText(item, "description", "선택된 부품 없음"));
                    p.setPrice(cleanPrice(getText(item, "price", "0")));
                    pick.put(p.getType(), p);
                }
            }

            // components / products 배열 처리
            JsonNode listNode = null;
            if (root.has("components")) listNode = root.get("components");
            else if (root.has("products")) listNode = root.get("products");

            if (listNode != null && listNode.isArray()) {
                for (JsonNode node : listNode) {
                    Product p = new Product();
                    p.setId(getText(node, "id", null));
                    String type = getText(node, "type",
                            getText(node, "category",
                                    getText(node, "product_type", "unknown")));
                    p.setType(normalizeType(type));
                    p.setName(getText(node, "name", "미선택"));
                    p.setDescription(getText(node, "description", "선택된 부품 없음"));
                    p.setPrice(cleanPrice(getText(node, "price", "0")));
                    pick.put(p.getType(), p);
                }
            }

            // fallbackMap 적용
            if (fallbackMap != null && !fallbackMap.isEmpty()) {
                for (var entry : fallbackMap.entrySet()) {
                    String type = normalizeType(entry.getKey());
                    Product fb = entry.getValue();
                    Product exist = pick.get(type);
                    if (exist == null || "미선택".equalsIgnoreCase(exist.getName())) {
                        pick.put(type, fb);
                    }
                }
            }

            // 누락된 카테고리 보정 및 정렬
            List<Product> finalList = new ArrayList<>();
            for (String cat : SERIES_ORDER) {
                Product exist = pick.get(cat);
                if (exist != null) {
                    if (isBlank(exist.getName())) exist.setName("미선택");
                    if (isBlank(exist.getPrice())) exist.setPrice("0");
                    finalList.add(exist);
                } else {
                    finalList.add(defaultProduct(cat));
                }
            }
            result.setProducts(finalList);

            // totalPrice 보정
            if (isBlank(result.getTotalPrice()) || "0".equals(stripWon(result.getTotalPrice()))) {
                long sum = finalList.stream()
                        .mapToLong(p -> parseLong(stripWon(p.getPrice())))
                        .sum();
                result.setTotalPrice(Long.toString(sum));
            }

            log.info("EstimateResult 파싱 완료: {} ({}개 부품)", result.getBuildName(), finalList.size());
            return result;

        } catch (Exception e) {
            log.error("GPT 응답 파싱 실패: {}", e.getMessage());
            EstimateResult fallback = new EstimateResult();
            fallback.setBuildName("AI 견적");
            fallback.setTotalPrice("0");
            fallback.setNotes("GPT 응답 파싱 실패");
            fallback.setBuildDescription("임시 기본 견적입니다.");
            fallback.setProducts(List.of());
            fallback.setAnotherInputText(List.of());
            fallback.setText("");
            return fallback;
        }
    }

    /** JSON 문자열만 추출 */
    private String extractJsonOnly(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        Pattern code = Pattern.compile("```json\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
        Matcher m1 = code.matcher(t);
        if (m1.find()) return sanitizeNumbers(m1.group(1));
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) return sanitizeNumbers(t.substring(first, last + 1));
        return "{}";
    }

    private String sanitizeNumbers(String j) {
        if (j == null) return null;
        return j.replaceAll("(?<=\\d)_(?=\\d)", "")
                .replaceAll("([0-9]),([0-9])", "$1$2")
                .replaceAll("원", "");
    }

    private String getText(JsonNode node, String key, String defaultVal) {
        if (node == null) return defaultVal;
        for (String alt : List.of(key, key.toLowerCase(), key.toUpperCase(),
                key.replace("_", ""), key.replace("_", "-"))) {
            if (node.has(alt) && !node.get(alt).isNull()) {
                return node.get(alt).asText();
            }
        }
        return defaultVal;
    }

    private String getText(JsonNode node, String key) {
        return getText(node, key, "");
    }

    /** 비견적성 응답 판단 로직 개선 */
    public boolean isNonEstimateResponse(EstimateResult result) {
        if (result == null) return true;

        // 부품이 존재하고, 실제로 하나라도 선택된 부품이 있으면 견적 응답으로 간주
        if (result.getProducts() != null && !result.getProducts().isEmpty()) {
            boolean anySelected = result.getProducts().stream().anyMatch(p ->
                    !"미선택".equalsIgnoreCase(p.getName()) &&
                            !"0".equals(stripWon(p.getPrice())) &&
                            !isBlank(p.getDescription())
            );
            if (anySelected) {
                return false; // 실제 견적 존재
            }
        }

        // 견적 이름(build_name) 또는 금액(totalPrice)이 유효하면 견적 응답으로 간주
        if (!isBlank(result.getBuildName()) && !"0".equals(stripWon(result.getTotalPrice()))) {
            return false;
        }

        // 위 조건이 모두 아니라면 text만 존재하는 비견적성 응답
        return result.getText() != null && !result.getText().isBlank();
    }


    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private Product defaultProduct(String type) {
        Product p = new Product();
        p.setType(type);
        p.setName("미선택");
        p.setDescription("선택된 부품 없음");
        p.setPrice("0");
        return p;
    }

    private String cleanPrice(String s) {
        if (s == null) return "0";
        String digits = stripWon(s);
        return digits.isEmpty() ? "0" : digits;
    }

    private String stripWon(String s) {
        return s.replaceAll("[^0-9]", "");
    }

    private long parseLong(String s) {
        try { return Long.parseLong(isBlank(s) ? "0" : s); }
        catch (Exception e) { return 0L; }
    }
}
