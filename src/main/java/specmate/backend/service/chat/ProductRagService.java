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

    /** type별로 개별 RAG 검색 수행 */
    public RagContext buildRagContext(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            log.warn("입력이 비어 있습니다. RAG 검색을 건너뜁니다.");
            return new RagContext("[]", new HashMap<>());
        }

        List<Map<String, Object>> resultsList = new ArrayList<>();

        for (String type : COMPONENT_TYPES) {
            try {
                // 기본 RAG 검색
                SearchRequest request = SearchRequest.builder()
                        .query(userInput + " " + type)
                        .topK(200)
                        .build();

                List<Document> docs = qdrantVectorStore.similaritySearch(request);

                // 가격 정보 필터: "가격비교예정" 또는 가격이 0인 경우 제외
                docs = docs.stream()
                        .filter(d -> {
                            Object priceInfo = d.getMetadata().get("price_info");
                            Object price = d.getMetadata().get("price");
                            String priceText = priceInfo == null ? "" : priceInfo.toString().toLowerCase(Locale.ROOT);
                            String priceValue = price == null ? "0" : price.toString().trim();
                            return !priceText.contains("가격비교예정") && !priceValue.equals("0");
                        })
                        .collect(Collectors.toList());

                // HDD의 NAS / 서버 / 감시 / 외장 계열 제외
                if (type.equals("hdd")) {
                    docs = docs.stream()
                            .filter(d -> {
                                String name = String.valueOf(d.getMetadata().get("name")).toLowerCase(Locale.ROOT);
                                return !name.contains("ironwolf")    // NAS용
                                        && !name.contains("exos")     // 서버용
                                        && !name.contains("red")      // NAS용
                                        && !name.contains("gold")     // 엔터프라이즈용
                                        && !name.contains("nas")      // NAS 관련
                                        && !name.contains("enterprise") // 서버용
                                        && !name.contains("server")   // 서버용
                                        && !name.contains("skyhawk")  // 감시용
                                        && !name.contains("purple")   // 감시용
                                        && !name.contains("expansion") // 외장형
                                        && !name.contains("elements"); // 외장형
                            })
                            .collect(Collectors.toList());
                }

                if (docs.isEmpty()) {
                    log.warn("[RAG] {} 검색 결과 없음 → fallback 실행", type);
                    docs = qdrantVectorStore.similaritySearch(
                            SearchRequest.builder().query(type).topK(100).build()
                    );
                }

                // type 필터 (gpu/vga 교차 허용)
                List<Document> filteredDocs = docs.stream()
                        .filter(d -> {
                            Object t = d.getMetadata().get("type");
                            if (t == null) return false;
                            String metaType = t.toString().toLowerCase(Locale.ROOT).trim();
                            return metaType.equals(type)
                                    || (type.equals("vga") && metaType.equals("gpu"))
                                    || (type.equals("gpu") && metaType.equals("vga"));
                        })
                        .collect(Collectors.toList());

                Document selectedDoc = null;

                // 필터된 결과가 있으면 첫 번째 문서 선택
                if (!filteredDocs.isEmpty()) {
                    selectedDoc = filteredDocs.get(0);
                }
                // 필터된 결과가 없으면 동일 type 내에서 pop_rank 높은 제품 선택
                else {
                    log.warn("[RAG] {} 일치 결과 없음 → 동일 type 내 pop_rank fallback 시도", type);

                    selectedDoc = docs.stream()
                            .filter(d -> {
                                Object t = d.getMetadata().get("type");
                                if (t == null) return false;
                                String metaType = t.toString().toLowerCase(Locale.ROOT).trim();
                                return metaType.equals(type)
                                        || (type.equals("vga") && metaType.equals("gpu"))
                                        || (type.equals("gpu") && metaType.equals("vga"));
                            })
                            .filter(d -> d.getMetadata().get("pop_rank") != null)
                            .min(Comparator.comparingInt(d -> {
                                try {
                                    return Integer.parseInt(d.getMetadata().get("pop_rank").toString());
                                } catch (Exception e) {
                                    return Integer.MAX_VALUE;
                                }
                            }))
                            .orElse(null);
                }

                // 그래도 없으면 전체 컬렉션에서 동일 type + pop_rank 순으로 검색
                if (selectedDoc == null) {
                    log.warn("[RAG] {} 관련 문서 없음 → 전체 컬렉션 pop_rank fallback", type);

                    List<Document> globalDocs = qdrantVectorStore.similaritySearch(
                            SearchRequest.builder().query(type).topK(300).build()
                    );

                    selectedDoc = globalDocs.stream()
                            .filter(d -> {
                                Object t = d.getMetadata().get("type");
                                if (t == null) return false;
                                String metaType = t.toString().toLowerCase(Locale.ROOT).trim();
                                return metaType.equals(type)
                                        || (type.equals("vga") && metaType.equals("gpu"))
                                        || (type.equals("gpu") && metaType.equals("vga"));
                            })
                            .filter(d -> d.getMetadata().get("pop_rank") != null)
                            .min(Comparator.comparingInt(d -> {
                                try {
                                    return Integer.parseInt(d.getMetadata().get("pop_rank").toString());
                                } catch (Exception e) {
                                    return Integer.MAX_VALUE;
                                }
                            }))
                            .orElse(null);
                }

                // 최종 선택 확인
                if (selectedDoc == null) {
                    log.warn("[RAG] {} 타입 제품을 찾지 못했습니다. (skip)", type);
                    continue;
                }

                // 결과 구성
                Map<String, Object> meta = selectedDoc.getMetadata();

                boolean isFallback = filteredDocs.isEmpty();
                String description = isFallback ? "인기순위 기반 추천 부품" : "추천 부품 설명 없음";

                Map<String, Object> comp = new LinkedHashMap<>();
                comp.put("type", type);
                comp.put("name", meta.getOrDefault("name", "데이터 없음"));
                comp.put("description", description);
                comp.put("detail", Map.of(
                        "price", meta.getOrDefault("price", "0"),
                        "image", meta.getOrDefault("image", "")
                ));

                resultsList.add(comp);

                log.info("[RAG] {} → {} (pop_rank={}, fallback={})",
                        type,
                        meta.getOrDefault("name", "데이터 없음"),
                        meta.getOrDefault("pop_rank", "N/A"),
                        isFallback);

            } catch (Exception e) {
                log.error("[RAG] {} 검색 실패: {}", type, e.getMessage(), e);
            }
        }

        // JSON 변환
        String ragJson = toJson(resultsList);
        log.info("[DEBUG] RAG JSON (GPT 전달용):\n{}", ragJson);

        return new RagContext(ragJson, new HashMap<>());
    }

    /** 기존 견적 기반 RAG - 재구성 */
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
