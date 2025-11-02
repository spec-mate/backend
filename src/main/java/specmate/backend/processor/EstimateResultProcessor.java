package specmate.backend.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.stereotype.Component;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.EstimateResult.Product;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EstimateResultProcessor {

    private final ObjectMapper objectMapper;
    private final QdrantVectorStore qdrantVectorStore;

    private static final List<String> SERIES_ORDER = List.of(
            "case", "cpu", "vga", "ram", "ssd", "power", "mainboard", "cooler", "hdd"
    );

    private static final Map<String, String> MAIN_KEY_NORMALIZER;
    static {
        Map<String, String> m = new HashMap<>();
        for (String k : List.of("case", "chassis", "tower")) m.put(k, "case");
        for (String k : List.of("cpu", "processor")) m.put(k, "cpu");
        for (String k : List.of("vga", "gpu", "graphics", "graphic_card", "video_card")) m.put(k, "vga");
        for (String k : List.of("ram", "ram_memory", "memory", "dimm", "ddr", "ddr4", "ddr5")) m.put(k, "ram");
        for (String k : List.of("ssd", "nvme", "m2", "m_2", "solid_state_drive", "storage")) m.put(k, "ssd");
        for (String k : List.of("power", "psu", "power_supply", "smps")) m.put(k, "power");
        for (String k : List.of("mainboard", "motherboard", "mb")) m.put(k, "mainboard");
        for (String k : List.of("cooler", "cooling", "cpu_cooler")) m.put(k, "cooler");
        for (String k : List.of("hdd", "harddisk", "hard_drive")) m.put(k, "hdd");
        MAIN_KEY_NORMALIZER = m;
    }

    private static String normalizeType(String raw) {
        if (raw == null) return "unknown";
        String s = raw.trim().toLowerCase(Locale.ROOT);

        // 정확 매칭 우선
        if (MAIN_KEY_NORMALIZER.containsKey(s)) return MAIN_KEY_NORMALIZER.get(s);

        // 부분 매칭 보조
        if (s.contains("case") || s.contains("tower") || s.contains("chassis")) return "case";
        if (s.contains("cpu") || s.contains("processor")) return "cpu";
        if (s.contains("gpu") || s.contains("graphics") || s.contains("vga")) return "vga";
        if (s.contains("ram") || s.contains("memory") || s.contains("ddr")) return "ram";
        if (s.contains("ssd") || s.contains("nvme") || s.contains("m.2") || s.contains("solid")) return "ssd";
        if (s.contains("power") || s.contains("psu") || s.contains("supply")) return "power";
        if (s.contains("mainboard") || s.contains("motherboard") || s.contains("board")) return "mainboard";
        if (s.contains("cooler") || s.contains("fan") || s.contains("heatsink")) return "cooler";
        if (s.contains("hdd") || s.contains("hard") || s.contains("storage")) return "hdd";

        return "unknown";
    }

    /** GPT 응답 → EstimateResult 변환 */
    public EstimateResult parse(String gptMessage, Map<String, Product> fallbackMap) {
        try {
            // ObjectMapper 설정 강화 (주석, 비인용 필드 허용)
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);

            String cleaned = extractJsonOnly(gptMessage);

            if (cleaned == null || cleaned.isBlank() || "{}".equals(cleaned)) {
                log.debug("자연어 응답 감지 → text로 저장");
                EstimateResult result = new EstimateResult();
                result.setProducts(List.of());
                return result;
            }

            // 배열 형태(JSON Array) 감지 시 별도 처리
            if (cleaned.trim().startsWith("[")) {
                log.info("[Parser] GPT 응답이 배열 형식으로 감지됨 → components 직접 파싱 모드");
                JsonNode listNode = objectMapper.readTree(cleaned);

                EstimateResult result = new EstimateResult();
                List<Product> products = new ArrayList<>();

                for (JsonNode node : listNode) {
                    Product p = new Product();
                    String type = getText(node, "type", "unknown");
                    p.setType(type);
                    p.setName(getText(node, "name", "데이터 없음"));
                    p.setDescription(getText(node, "description", "추천 부품 없음"));

                    JsonNode detail = node.get("detail");
                    if (detail != null) {
                        p.setDetail(new EstimateResult.Detail(
                                getText(detail, "price", "0"),
                                getText(detail, "image", "")
                        ));
                    } else {
                        p.setDetail(new EstimateResult.Detail("0", ""));
                    }
                    products.add(p);
                }

                result.setProducts(products);
                result.setBuildName("RAG 자동 구성 견적");
                result.setTotalPrice(products.stream()
                        .mapToInt(pr -> parsePrice(pr.getDetail() != null ? pr.getDetail().getPrice() : "0"))
                        .sum());
                result.setAnotherInputText(List.of());
                return result;
            }

            // 기존 로직 (JSON Object 형태 그대로 유지)
            JsonNode root = objectMapper.readTree(cleaned);
            EstimateResult result = new EstimateResult();

            result.setAiEstimateId(getText(root, "ai_estimate_id", ""));
            result.setBuildName(getText(root, "build_name", ""));
            result.setBuildDescription(getText(root, "build_description", ""));
            result.setTotalPrice(parsePrice(getText(root, "total", "0")));

            // another_input_text
            if (root.has("another_input_text") && root.get("another_input_text").isArray()) {
                List<String> qList = new ArrayList<>();
                for (JsonNode q : root.get("another_input_text")) qList.add(q.asText());
                result.setAnotherInputText(qList);
            } else {
                result.setAnotherInputText(List.of());
            }

            Map<String, Product> pick = new LinkedHashMap<>();
            JsonNode listNode = root.has("components") ? root.get("components")
                    : root.has("products") ? root.get("products") : null;

            if (listNode != null) {
                if (listNode.isArray()) {
                    for (JsonNode node : listNode) {
                        String type = normalizeType(getText(node, "type",
                                getText(node, "category", getText(node, "product_type", "unknown"))));
                        parseProductNode(pick, node, type);
                    }
                } else if (listNode.isObject()) {
                    listNode.fieldNames().forEachRemaining(field -> {
                        JsonNode node = listNode.get(field);
                        String type = normalizeType(field);
                        parseProductNode(pick, node, type);
                    });
                }
            }

            List<Product> normalized = normalizeComponents(new ArrayList<>(pick.values()));
            result.setProducts(normalized);

            // totalPrice 재계산
            if (result.getTotalPrice() == null || result.getTotalPrice() == 0) {
                long sum = normalized.stream()
                        .filter(p -> p.getDetail() != null)
                        .mapToLong(p -> parseLong(stripWon(p.getDetail().getPrice())))
                        .sum();
                result.setTotalPrice((int) sum);
            }

            // 총 가격 불일치 시 자동 보정
            if (result.getProducts() != null && !result.getProducts().isEmpty()) {
                long calcSum = result.getProducts().stream()
                        .filter(p -> p.getDetail() != null)
                        .mapToLong(p -> {
                            try {
                                return Math.round(Double.parseDouble(p.getDetail().getPrice().replaceAll("[^0-9.]", "")));
                            } catch (Exception e) {
                                return 0L;
                            }
                        })
                        .sum();

                Integer currentTotal = result.getTotalPrice();
                if (currentTotal == null || Math.abs(currentTotal - calcSum) > 1) {
                    log.info("총 가격 불일치 감지 → GPT total={}, 실제 합계={} → 보정 적용", currentTotal, calcSum);
                    result.setTotalPrice((int) calcSum);
                }
            }

            log.info("EstimateResult 파싱 완료: {} ({}개 부품)", result.getBuildName(), normalized.size());
            return result;

        } catch (Exception e) {
            log.error("GPT 응답 파싱 실패: {}", e.getMessage(), e);
            EstimateResult fallback = new EstimateResult();
            fallback.setBuildName("AI 견적");
            fallback.setTotalPrice(0);
            fallback.setProducts(List.of());
            fallback.setAnotherInputText(List.of());
            return fallback;
        }
    }

    /** 중복 제거 및 누락 타입 채우기 */
    private List<Product> normalizeComponents(List<Product> products) {
        Map<String, Product> uniqueMap = new LinkedHashMap<>();
        for (Product p : products) {
            if (p == null || p.getType() == null) continue;
            String type = normalizeType(p.getType());
            uniqueMap.putIfAbsent(type, p);
        }

        for (String type : SERIES_ORDER) {
            if (!uniqueMap.containsKey(type)) {
                uniqueMap.put(type, defaultProduct(type));
            }
        }

        uniqueMap.remove("unknown");

        return SERIES_ORDER.stream()
                .map(uniqueMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

    }

    /** 중복 허용 안 함 */
    private void parseProductNode(Map<String, Product> pick, JsonNode node, String type) {
        if (node == null || node.isNull()) return;
        String normalizedType = normalizeType(type);
        if (pick.containsKey(normalizedType)) return; // 중복 방지

        Product p = new Product();
        p.setId(getText(node, "id", null));
        p.setType(normalizedType);

        p.setAiName(getText(node, "ai_name", getText(node, "name", "미선택")));

        p.setName(getText(node, "name", getText(node, "matched_name", "미선택")));
        p.setDescription(getText(node, "description", "선택된 부품 없음"));

        JsonNode detailNode = node.get("detail");
        String price = detailNode != null ? getText(detailNode, "price", "0") : getText(node, "price", "0");
        String image = detailNode != null ? getText(detailNode, "image", "") : getText(node, "image", "");
        p.setDetail(new EstimateResult.Detail(price, image));

        pick.put(normalizedType, p);
    }


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
        if (node != null && node.has(key) && !node.get(key).isNull()) {
            return node.get(key).asText();
        }
        return defaultVal;
    }

    private String getText(JsonNode node, String key) {
        return getText(node, key, "");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private Product defaultProduct(String type) {
        Product p = new Product();
        p.setType(type);
        p.setName("데이터 없음");
        p.setDescription("선택된 부품 없음");
        p.setDetail(new EstimateResult.Detail("0", ""));
        return p;
    }

    private int parsePrice(String priceStr) {
        try {
            return (int) Double.parseDouble(priceStr.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private String stripWon(String s) {
        return s.replaceAll("[^0-9.]", "");
    }

    private long parseLong(String s) {
        try {
            return (long) Double.parseDouble(isBlank(s) ? "0" : s);
        } catch (Exception e) {
            return 0L;
        }
    }
}
