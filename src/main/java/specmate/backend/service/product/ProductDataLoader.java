//package specmate.backend.service.product;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//import specmate.backend.entity.Product;
//import specmate.backend.repository.product.ProductRepository;
//
//import java.io.InputStream;
//import java.util.List;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ProductDataLoader implements CommandLineRunner {
//
//    private final ObjectMapper objectMapper;
//    private final ProductRepository productRepository;
//
//    @Override
//    public void run(String... args) throws Exception {
//        if (productRepository.count() > 0) {
//            log.info("✅ Product 데이터 이미 존재. 로드 건너뜀.");
//            return;
//        }
//
//        InputStream inputStream = getClass().getResourceAsStream("/merged.json");
//        if (inputStream == null) {
//            log.error("❌ merged.json 파일을 찾을 수 없습니다. /resources/merged.json 경로를 확인하세요.");
//            return;
//        }
//
//        try {
//            List<Product> products = objectMapper.readValue(inputStream, new TypeReference<>() {});
//            productRepository.saveAll(products);
//            log.info("✅ {}개의 Product 데이터 삽입 완료.", products.size());
//        } catch (Exception e) {
//            log.error("❌ Product 데이터 삽입 실패: {}", e.getMessage());
//        }
//    }
//}
