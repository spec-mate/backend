package specmate.backend.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.ProductEmbedding;
import specmate.backend.repository.embedding.ProductEmbeddingRepository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ProductRagService {

    private final ProductEmbeddingRepository embeddingRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> ALL_TYPES = List.of(
            "case", "cpu", "vga", "ram", "ssd", "power", "mainboard", "cooler", "hdd"
    );

    /** 사용자 입력 기반 RAG 컨텍스트 생성 (DB의 context 필드 기반) */
    public RagContext buildRagContext(String userInput) {
        try {
            // 입력 문장 임베딩 생성
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(userInput));
            float[] queryVector = response.getResults().get(0).getOutput();
            String queryVectorString = toPgVectorString(queryVector);

            // 카테고리별 유사 제품 검색
            List<Map<String, Object>> ragItems = new ArrayList<>();
            Map<String, EstimateResult.Product> fallbackMap = new LinkedHashMap<>();

            for (String type : ALL_TYPES) {
                List<ProductEmbedding> results =
                        embeddingRepository.findNearestByNormalizedType(queryVectorString, type, 5);

                if (!results.isEmpty()) {
                    ProductEmbedding top = results.get(0);

                    String name = extractField(top.getContent(), "제품명");
                    String manufacturer = extractField(top.getContent(), "제조사");
                    String price = extractField(top.getContent(), "최저가").replaceAll("[^0-9]", "");
                    String image = extractField(top.getContent(), "이미지URL");
                    String desc = manufacturer + " " + type.toUpperCase() + " 구성";

                    ragItems.add(Map.of(
                            "name", name,
                            "manufacturer", manufacturer,
                            "category", type,
                            "price", price,
                            "image_url", image
                    ));

                    fallbackMap.put(type, EstimateResult.Product.builder()
                            .type(type)
                            .matchedName(name)
                            .price(price)
                            .description(desc)
                            .image(image)
                            .build());
                }
            }

            //  JSON 직렬화
            String ragJson = objectMapper.writeValueAsString(ragItems);
            return new RagContext(ragJson, fallbackMap);

        } catch (Exception e) {
            e.printStackTrace();
            return new RagContext("[]", Collections.emptyMap());
        }
    }

    /** Danawa product_embeddings.context 문자열에서 특정 필드 추출 */
    private String extractField(String context, String key) {
        if (context == null || key == null) return "";
        try {
            int start = context.indexOf(key + ":");
            if (start == -1) return "";
            int endComma = context.indexOf(",", start);
            int endNewLine = context.indexOf("\n", start);
            int end = Math.min(
                    endComma != -1 ? endComma : context.length(),
                    endNewLine != -1 ? endNewLine : context.length()
            );
            return context.substring(start + key.length() + 1, end).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String toPgVectorString(float[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }

    @Getter
    @RequiredArgsConstructor
    public static class RagContext {
        private final String contextJson;  // JSON 배열 형태
        private final Map<String, EstimateResult.Product> fallbackMap;
    }
}
