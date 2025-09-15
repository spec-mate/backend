package specmate.backend.service.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import specmate.backend.dto.product.ProductJsonDto;
import specmate.backend.entity.Product;
import specmate.backend.repository.product.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public void importFromJsonOnce() {
        try {
            if (productRepository.count() > 0) {
                System.out.println("[INFO] Products already exist, skipping JSON import.");
                return;
            }

            ClassPathResource resource =
                    new ClassPathResource("merged.json");

            List<ProductJsonDto> dtoList = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<ProductJsonDto>>() {}
            );

            List<Product> products = dtoList.stream()
                    .map(this::toEntity)
                    .toList();

            productRepository.saveAll(products);
            System.out.println("[INFO] Imported " + products.size() + " products from JSON.");
        } catch (Exception e) {
            throw new RuntimeException("JSON import 실패", e);
        }
    }

    private Product toEntity(ProductJsonDto dto) {
        return Product.builder()
                .name(dto.getName())
                .image(dto.getImage())
                .popRank(dto.getPop_rank())
                .regDate(dto.getReg_date())
                .options(dto.getOptions())
                .priceInfo(dto.getPrice_info())
                .lowestPrice(dto.getLowest_price())
                .type(dto.getType())
                .manufacturer(dto.getManufacturer())
                .build();
    }
}