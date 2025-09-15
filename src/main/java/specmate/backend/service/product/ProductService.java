package specmate.backend.service.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import specmate.backend.dto.product.ProductRequest;
import specmate.backend.dto.product.ProductResponse;
import specmate.backend.entity.Product;
import specmate.backend.repository.product.ProductRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .image(product.getImage())
                .popRank(product.getPopRank())
                .regDate(product.getRegDate())
                .options(product.getOptions())
                .priceInfo(product.getPriceInfo())
                .lowestPrice(product.getLowestPrice())
                .type(product.getType())
                .manufacturer(product.getManufacturer())
                .build();
    }

    private Product toEntity(ProductRequest request) {
        return Product.builder()
                .name(request.getName())
                .image(request.getImage())
                .popRank(request.getPopRank())
                .regDate(request.getRegDate())
                .options(request.getOptions())
                .priceInfo(request.getPriceInfo())
                .lowestPrice(request.getLowestPrice())
                .type(request.getType())
                .manufacturer(request.getManufacturer())
                .build();
    }

    public Page<ProductResponse> getProductsByType(
            String type,
            String manufacturer,
            String sort,
            Pageable pageable
    ) {
        Page<Product> products;

        if (manufacturer != null && !manufacturer.isEmpty()) {
            products = productRepository.findByTypeAndManufacturer(type, manufacturer, pageable);
        } else {
            if ("priceAsc".equalsIgnoreCase(sort)) {
                products = productRepository.findByTypeOrderByLowestPriceAsc(type, pageable);
            } else if ("priceDesc".equalsIgnoreCase(sort)) {
                products = productRepository.findByTypeOrderByLowestPriceDesc(type, pageable);
            } else {
                products = productRepository.findByType(type, pageable);
            }
        }

        return products.map(this::toResponse);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse getProduct(Integer id) {
        return productRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public ProductResponse createProduct(ProductRequest request) {
        Product product = toEntity(request);
        return toResponse(productRepository.save(product));
    }

    public ProductResponse updateProduct(Integer id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setName(request.getName());
        product.setImage(request.getImage());
        product.setPopRank(request.getPopRank());
        product.setRegDate(request.getRegDate());
        product.setOptions(request.getOptions());
        product.setPriceInfo(request.getPriceInfo());
        product.setLowestPrice(request.getLowestPrice());
        product.setType(request.getType());
        product.setManufacturer(request.getManufacturer());

        return toResponse(productRepository.save(product));
    }

    public void deleteProduct(Integer id) {
        productRepository.deleteById(id);
    }
}