//package specmate.backend.service.embedding;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.embedding.Embedding;
//import org.springframework.ai.embedding.EmbeddingResponse;
//import org.springframework.ai.openai.OpenAiEmbeddingModel;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import specmate.backend.entity.Product;
//import specmate.backend.entity.ProductEmbedding;
//import specmate.backend.repository.embedding.ProductEmbeddingRepository;
//import specmate.backend.repository.product.ProductRepository;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class ProductEmbeddingService {
//
//    private final ProductRepository productRepository;
//    private final ProductEmbeddingRepository embeddingRepository;
//    private final OpenAiEmbeddingModel embeddingModel;
//
//    @Transactional
//    public void generateEmbeddingsForAll() {
//        List<Product> products = productRepository.findAll();
//
//        for (Product product : products) {
//            try {
//                // 이미 임베딩이 있으면 건너뜀
//                if (embeddingRepository.findByProductId(product.getId()).isPresent()) {
//                    continue;
//                }
//
//                String content = buildEmbeddingText(product);
//
//                EmbeddingResponse response = embeddingModel.embed(List.of(content));
//                Embedding embedding = response.getData().get(0);
//                float[] vector = embedding.getEmbedding();
//
//                ProductEmbedding pe = ProductEmbedding.builder()
//                        .product(product)
//                        .embedding(vector)
//                        .content(content)
//                        .createdAt(LocalDateTime.now())
//                        .build();
//
//                embeddingRepository.save(pe);
//                log.info("{} 임베딩 완료", product.getName());
//
//            } catch (Exception e) {
//                log.error("{} 임베딩 실패: {}", product.getName(), e.getMessage());
//            }
//        }
//    }
//
//    private String buildEmbeddingText(Product p) {
//        return String.format(
//                "제품명: %s, 제조사: %s, 유형: %s, 주요 옵션: %s",
//                p.getName(),
//                p.getManufacturer(),
//                p.getType(),
//                p.getOptions() != null ? p.getOptions().toString() : ""
//        );
//    }
//}
