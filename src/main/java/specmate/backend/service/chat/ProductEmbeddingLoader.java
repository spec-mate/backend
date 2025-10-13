package specmate.backend.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import specmate.backend.entity.Product;
import specmate.backend.repository.product.ProductRepository;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEmbeddingLoader implements CommandLineRunner {

    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final VectorStore vectorStore;

    @Override
    public void run(String... args) throws Exception {
        long totalCount = productRepository.count();
        if (totalCount == 0) {
            log.warn("Product 데이터가 없습니다. 임베딩 로드를 건너뜁니다.");
            return;
        }

        log.info("총 {}개의 Product 데이터를 불러와 임베딩합니다.", totalCount);

        // JSON 파일 로드
        InputStream inputStream = getClass().getResourceAsStream("/merged.json");
        if (inputStream == null) {
            log.error("merged.json 파일을 찾을 수 없습니다.");
            return;
        }

        List<Map<String, Object>> rawData = objectMapper.readValue(inputStream, new TypeReference<>() {});
        log.info("merged.json 로드 완료: {}개 데이터", rawData.size());

        int batchSize = 20;
        for (int i = 0; i < rawData.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, rawData.size());
            List<Map<String, Object>> batch = rawData.subList(i, endIndex);

            List<Document> documents = batch.stream()
                    .map(item -> {
                        try {
                            String name = (String) item.get("name");
                            String type = (String) item.get("type");
                            String manufacturer = (String) item.get("manufacturer");

                            Map<String, Object> options = (Map<String, Object>) item.get("options");
                            String optionsJson = objectMapper.writeValueAsString(options);

                            String content = String.format(
                                    "제품명: %s\n제조사: %s\n분류: %s\n옵션: %s",
                                    name, manufacturer, type, optionsJson
                            );

                            return new Document(
                                    name,
                                    content,
                                    Map.of(
                                            "name", name,
                                            "type", type,
                                            "manufacturer", manufacturer
                                    )
                            );
                        } catch (Exception e) {
                            log.warn("문서 변환 실패: {}", item.get("name"), e);
                            return null;
                        }
                    })
                    .filter(doc -> doc != null)
                    .collect(Collectors.toList());

            try {
                vectorStore.add(documents);
                log.info("{}~{}번 배치 저장 완료 ({}개)", i, endIndex, documents.size());
            } catch (Exception e) {
                log.error("VectorStore 저장 중 오류 ({}~{}): {}", i, endIndex, e.getMessage());
            }

            Thread.sleep(500); // rate limit 방지
        }

        log.info("모든 Product 임베딩 및 저장이 완료되었습니다!");
    }
}
