//package specmate.backend.service.embedding;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.embedding.EmbeddingResponse;
//import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//import specmate.backend.entity.Product;
//import specmate.backend.repository.product.ProductRepository;
//
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ProductEmbeddingLoader implements CommandLineRunner {
//
//    private final ProductRepository productRepository;
//    private final EmbeddingModel embeddingModel;
//    private final QdrantVectorStore qdrantVectorStore;
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    private static final int BATCH_SIZE = 5;
//    private static final int THREAD_COUNT = 2;
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//        long total = productRepository.count();
//        if (total == 0) {
//            log.warn("Product 데이터가 없습니다. 임베딩 생성을 건너뜁니다.");
//            return;
//        }
//
//        log.info("총 {}개의 제품 데이터를 임베딩 처리합니다.", total);
//
//        List<Product> allProducts = productRepository.findAll();
//        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
//        List<Future<Integer>> futures = new ArrayList<>();
//
//        for (int i = 0; i < allProducts.size(); i += BATCH_SIZE) {
//            int start = i;
//            int end = Math.min(i + BATCH_SIZE, allProducts.size());
//            List<Product> batch = allProducts.subList(start, end);
//            futures.add(executor.submit(() -> processBatch(batch)));
//        }
//
//        int totalProcessed = 0;
//        for (Future<Integer> f : futures) totalProcessed += f.get();
//
//        executor.shutdown();
//        log.info("총 {}개의 제품 임베딩을 Qdrant 컬렉션에 저장 완료.", totalProcessed);
//    }
//
//    /** === 개별 배치 처리 === */
//    private int processBatch(List<Product> batch) {
//        try {
//            List<String> contents = batch.stream()
//                    .map(Product::getName)
//                    .filter(Objects::nonNull)
//                    .map(String::trim)
//                    .filter(s -> !s.isBlank())
//                    .toList();
//
//            if (contents.isEmpty()) return 0;
//
//            EmbeddingResponse response = embeddingModel.embedForResponse(contents);
//            List<float[]> vectors = response.getResults().stream()
//                    .map(r -> r.getOutput())
//                    .toList();
//
//            List<Document> docs = new ArrayList<>();
//
//            for (int i = 0; i < batch.size(); i++) {
//                Product p = batch.get(i);
//                float[] vector = vectors.get(i);
//
//                Map<String, Object> metadata = new LinkedHashMap<>();
//                metadata.put("product_id", String.valueOf(p.getId()));
//                metadata.put("manufacturer", Optional.ofNullable(p.getManufacturer()).orElse("unknown"));
//                metadata.put("type", Optional.ofNullable(p.getType()).orElse("unknown"));
//                metadata.put("price", (int) Math.round(parsePriceToDouble(p)));
//                metadata.put("color", extractColorFromName(p.getName()));
//                metadata.put("created_at", LocalDateTime.now().toString());
//                metadata.put("name", p.getName());
//                metadata.put("image", Optional.ofNullable(p.getImage()).orElse(""));
//
//                // pop_rank (인기순위) 추가
//                metadata.put("pop_rank", p.getPopRank() != null ? p.getPopRank() : 9999);
//
//                // options JSON 직렬화
//                try {
//                    String optionsJson = objectMapper.writeValueAsString(p.getOptions());
//                    metadata.put("options", optionsJson);
//                } catch (Exception e) {
//                    metadata.put("options", "{}");
//                    log.warn("options 직렬화 실패 (id={}): {}", p.getId(), e.getMessage());
//                }
//
//                docs.add(new Document(p.getName(), metadata));
//            }
//
//            qdrantVectorStore.add(docs);
//            return docs.size();
//
//        } catch (Exception e) {
//            log.error("배치 처리 중 오류 발생: {}", e.getMessage(), e);
//            return 0;
//        }
//    }
//
//    /** === 가격 파싱 === */
//    private double parsePriceToDouble(Product p) {
//        try {
//            if (p.getLowestPrice() == null) return 0.0;
//            Object priceObj = p.getLowestPrice().get("price");
//            if (priceObj == null) return 0.0;
//            return Double.parseDouble(priceObj.toString().replaceAll("[^0-9]", ""));
//        } catch (Exception e) {
//            return 0.0;
//        }
//    }
//
//    /** === 색상 추출 === */
//    private String extractColorFromName(String name) {
//        String lower = name.toLowerCase();
//        if (lower.contains("white") || lower.contains("화이트")) return "white";
//        if (lower.contains("black") || lower.contains("블랙")) return "black";
//        if (lower.contains("pink") || lower.contains("핑크")) return "pink";
//        if (lower.contains("blue") || lower.contains("블루")) return "blue";
//        if (lower.contains("red") || lower.contains("레드")) return "red";
//        if (lower.contains("rgb")) return "rgb";
//        return "default";
//    }
//}
