package specmate.backend.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import specmate.backend.entity.Product;
import specmate.backend.repository.product.ProductRepository;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEmbeddingLoader implements CommandLineRunner {

    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final OpenAiEmbeddingModel embeddingModel;

    @Override
    public void run(String... args) throws Exception {

        long totalCount = productRepository.count();
        long unembeddedCount = productRepository.countByVectorIsNull();

        if (unembeddedCount == 0) {
            log.info("모든 Product에 임베딩이 이미 존재합니다. 로드를 건너뜁니다. (총 {}개)", totalCount);
            return;
        }

        log.info("총 {}개 중 {}개 제품에 임베딩이 없습니다. 임베딩을 시작합니다.", totalCount, unembeddedCount);

        // JSON 파일 로드
        InputStream inputStream = getClass().getResourceAsStream("/merged.json");
        if (inputStream == null) {
            log.error("merged.json 파일을 찾을 수 없습니다.");
            return;
        }

        List<Map<String, Object>> rawData = objectMapper.readValue(inputStream, new TypeReference<>() {});
        log.info("JSON 로드 완료: {}개 데이터", rawData.size());

        int batchSize = 20;
        for (int i = 0; i < rawData.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, rawData.size());
            List<Map<String, Object>> batch = rawData.subList(i, endIndex);

            for (Map<String, Object> item : batch) {
                try {
                    String name = (String) item.get("name");

                    List<Product> duplicates = productRepository.findAllByName(name);
                    Product existing = duplicates.isEmpty() ? null : duplicates.get(0);

                    if (existing != null && existing.getVector() != null) {
                        continue;
                    }

                    String type = (String) item.get("type");
                    String manufacturer = (String) item.get("manufacturer");
                    String image = (String) item.getOrDefault("image", "");
                    Integer popRank = item.get("pop_rank") instanceof Number ? ((Number) item.get("pop_rank")).intValue() : null;
                    String regDate = (String) item.getOrDefault("reg_date", "");

                    Map<String, Object> lowestPrice = (Map<String, Object>) item.get("lowest_price");
                    List<Map<String, Object>> priceInfo = (List<Map<String, Object>>) item.get("price_info");
                    Map<String, Object> options = (Map<String, Object>) item.get("options");

                    String optionsJson = objectMapper.writeValueAsString(options);
                    String text = name + " " + type + " " + manufacturer + " " + optionsJson;

                    // 임베딩 요청
                    EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
                    List<Embedding> results = response.getResults();
                    if (results.isEmpty()) continue;

                    float[] vector = results.get(0).getOutput();

                    // 이미 존재하면 update, 없으면 insert
                    Product product = existing != null ? existing : new Product();
                    product.setName(name);
                    product.setType(type);
                    product.setImage(image);
                    product.setPopRank(popRank);
                    product.setRegDate(regDate);
                    product.setLowestPrice(lowestPrice);
                    product.setPriceInfo(priceInfo);
                    product.setOptions(options);
                    product.setManufacturer(manufacturer);
                    product.setVector(vector);

                    productRepository.save(product);

                } catch (Exception e) {
                    log.warn("임베딩 실패: {}", item.get("name"), e);
                }
            }

            try {
                Thread.sleep(500); // API rate 제한 방지
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            log.info("배치 {}~{} 처리 완료", i, endIndex);
        }

        log.info("모든 누락된 제품 임베딩 완료!");
    }
}
