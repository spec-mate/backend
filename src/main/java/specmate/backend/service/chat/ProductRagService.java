package specmate.backend.service.chat;

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

    /** type별로 개별 RAG 검색 수행 (post-filter 방식) Qdrant의 payload.type 인식 문제 회피용 */
    public RagContext buildRagContext(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            log.warn("입력이 비어 있습니다. RAG 검색을 건너뜁니다.");
            return new RagContext("[]", new HashMap<>());
        }

        List<Map<String, Object>> resultsList = new ArrayList<>();

        for (String type : COMPONENT_TYPES) {
            try {
                // 유사도 검색 (필터 없이 전체 검색)
                SearchRequest request = SearchRequest.builder()
                        .query(userInput + " " + type)
                        .topK(200)
                        .build();

                List<Document> docs = qdrantVectorStore.similaritySearch(request);

                if (docs.isEmpty()) {
                    log.warn("[RAG] {} 검색 결과 없음 (fallback 실행)", type);

                    docs = qdrantVectorStore.similaritySearch(
                            SearchRequest.builder().query(type).topK(20).build()
                    );
                }

                // Java post-filter 사용
                Optional<Document> matchOpt = docs.stream()
                        .filter(d -> {
                            Object t = d.getMetadata().get("type");
                            if (t == null) return false;
                            String metaType = t.toString().toLowerCase(Locale.ROOT).trim();
                            return metaType.contains(type);
                        })
                        .findFirst();

                if (matchOpt.isEmpty()) {
                    log.warn("[RAG] {} 타입 일치 결과 없음 (skip)", type);
                    continue;
                }

                Document doc = matchOpt.get();
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
                log.info("[RAG] {} → {}", type, meta.getOrDefault("name", "데이터 없음"));

            } catch (Exception e) {
                log.error("[RAG] {} 검색 실패: {}", type, e.getMessage(), e);
            }
        }

        // 결과 JSON 변환
        String ragJson = toJson(resultsList);
        log.info("[DEBUG] RAG JSON (GPT 전달용):\n{}", ragJson);

        return new RagContext(ragJson, new HashMap<>());
    }

    /** 기존 견적 기반 RAG (재구성용) */
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

    /** JSON Formatter */
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
