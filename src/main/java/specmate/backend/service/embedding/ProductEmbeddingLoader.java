// 임베딩 모델 활용한 제품 데이터 Loader 코드
//package specmate.backend.service.embedding;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.embedding.EmbeddingResponse;
//import org.springframework.ai.openai.OpenAiEmbeddingModel;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//import specmate.backend.entity.Product;
//import specmate.backend.repository.embedding.ProductEmbeddingRepository;
//import specmate.backend.repository.product.ProductRepository;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//import java.util.stream.Collectors;
//import java.util.stream.IntStream;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ProductEmbeddingLoader implements CommandLineRunner {
//
//    private final ProductRepository productRepository;
//    private final ProductEmbeddingRepository embeddingRepository;
//    private final OpenAiEmbeddingModel embeddingModel;
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//        long totalProducts = productRepository.count();
//        if (totalProducts == 0) {
//            log.warn("Product 데이터가 없습니다. 임베딩 로드를 건너뜁니다.");
//            return;
//        }
//
//        log.info("총 {}개의 Product 데이터를 점검 후 임베딩합니다.", totalProducts);
//
//        List<Product> products = productRepository.findAll();
//        int batchSize = 20;
//
//        for (int i = 0; i < products.size(); i += batchSize) {
//            int end = Math.min(i + batchSize, products.size());
//            List<Product> batch = products.subList(i, end);
//
//            for (Product product : batch) {
//                try {
//                    Optional<?> existing = embeddingRepository.findByProductId(product.getId());
//                    if (existing.isPresent()) continue; // 이미 벡터 존재 시 skip
//
//                    String content = buildEmbeddingText(product);
//                    EmbeddingResponse response = embeddingModel.embedForResponse(List.of(content));
//                    float[] vector = response.getResults().get(0).getOutput();
//
//                    // float[] → 문자열 변환
//                    String vectorString = "[" + IntStream.range(0, vector.length)
//                            .mapToObj(idx -> Float.toString(vector[idx]))
//                            .collect(Collectors.joining(",")) + "]";
//
//                    // Repository에서 CAST(:vector AS vector) 로 저장
//                    embeddingRepository.insertWithVector(
//                            product.getId(),
//                            content,
//                            LocalDateTime.now(),
//                            vectorString
//                    );
//
//                    log.info("임베딩 완료: {}", product.getName());
//
//                } catch (Exception e) {
//                    log.error("임베딩 실패 - {}: {}", product.getName(), e.getMessage());
//                }
//            }
//
//            log.info("{}~{}번 배치 완료 ({}개)", i, end, batch.size());
//            Thread.sleep(1000); // rate limit 방지
//        }
//
//        long embeddedCount = embeddingRepository.count();
//        log.info("모든 Product 임베딩 완료. 총 {}개 저장됨.", embeddedCount);
//    }
//
//    private String buildEmbeddingText(Product p) {
//        return String.format(
//                "제품명: %s, 제조사: %s, 분류: %s, 주요 옵션: %s",
//                p.getName(),
//                p.getManufacturer(),
//                p.getType(),
//                p.getOptions() != null ? p.getOptions().toString() : ""
//        );
//    }
//}
