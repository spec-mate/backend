package specmate.backend.service.chat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.estimate.ai.RagContext;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRagService {

    private final QdrantVectorStore qdrantVectorStore;

    private static final List<String> COMPONENT_TYPES = List.of(
            "case", "cpu", "vga", "ram", "ssd", "power", "mainboard", "cooler", "hdd"
    );

    /**
     * type별로 개별 RAG 검색을 수행 (각 부품 1개씩 보장)
     */
    public RagContext buildRagContext(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            log.warn("입력이 비어 있습니다. RAG 검색을 건너뜁니다.");
            return new RagContext("[]", new HashMap<>());
        }

        List<Map<String, Object>> resultsList = new ArrayList<>();
        Set<String> addedTypes = new HashSet<>();

        for (String type : COMPONENT_TYPES) {
            try {
                SearchRequest request = SearchRequest.builder()
                        .query(userInput + " " + type)
                        .topK(5)
                        .similarityThresholdAll()
                        .build();

                List<Document> docs = qdrantVectorStore.similaritySearch(request);

                if (!docs.isEmpty()) {
                    // 중복 타입 방지
                    if (addedTypes.contains(type)) continue;

                    Document doc = docs.get(0);
                    Map<String, Object> meta = doc.getMetadata();

                    Map<String, Object> comp = new LinkedHashMap<>();
                    comp.put("type", type);
                    comp.put("name", meta.getOrDefault("name", "데이터 없음"));
                    comp.put("description", "추천 부품 설명 없음");
                    comp.put("detail", Map.of(
                            "price", meta.getOrDefault("price", "0"),
                            "image", meta.getOrDefault("image", "")
                    ));

                    resultsList.add(comp);
                    addedTypes.add(type);
                    log.info("[RAG] {} → {}", type, meta.getOrDefault("name", "데이터 없음"));
                } else {
                    log.warn("[RAG] {} 타입 결과 없음 (fallback)", type);
                }
            } catch (Exception e) {
                log.error("[RAG] {} 검색 실패: {}", type, e.getMessage());
            }
        }

        // JSON 문자열화
        String ragJson = toJson(resultsList);
        log.info("[DEBUG] RAG JSON (GPT 전달용):\n{}", ragJson);

        return new RagContext(ragJson, new HashMap<>());
    }

    /** 기존 EstimateResult 기반 RAG Context (재구성용) */
    public RagContext buildRagContext(EstimateResult estimateResult) {
        if (estimateResult == null || estimateResult.getProducts() == null) {
            return new RagContext("{}", new HashMap<>());
        }

        Map<String, EstimateResult.Product> fallbackMap = estimateResult.getProducts().stream()
                .filter(p -> p.getType() != null)
                .collect(Collectors.toMap(
                        p -> p.getType().toLowerCase(Locale.ROOT),
                        p -> p
                ));

        return new RagContext("{}", fallbackMap);
    }

    /** JSON 문자열 포맷터 */
    private String toJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> comp = list.get(i);
            sb.append("  {\n");
            sb.append("    \"type\": \"").append(comp.get("type")).append("\",\n");
            sb.append("    \"name\": \"").append(comp.get("name")).append("\",\n");
            sb.append("    \"description\": \"").append(comp.get("description")).append("\",\n");
            Map<String, Object> detail = (Map<String, Object>) comp.get("detail");
            sb.append("    \"detail\": {\n");
            sb.append("      \"price\": \"").append(detail.get("price")).append("\",\n");
            sb.append("      \"image\": \"").append(detail.get("image")).append("\"\n");
            sb.append("    }\n");
            sb.append("  }");
            if (i < list.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
}
