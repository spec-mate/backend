package specmate.backend.service.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import specmate.backend.dto.product.ProductRequest;
import specmate.backend.dto.product.ProductResponse;
import specmate.backend.entity.Product;
import specmate.backend.repository.product.ProductRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
            .id(product.getId())
            .popRank(product.getPopRank())
            .category(product.getCategory())
            .name(product.getName())
            .manufacturer(product.getManufacturer())
            .price(product.getPrice())
            .status(product.getStatus())
            .image(product.getImage())
            .specs(product.getSpecs())
            .productLink(product.getProductLink())
            .description(product.getDescription())
            .updatedAt(product.getUpdatedAt())
            .build();
    }

    private Product toEntity(ProductRequest req) {
        return Product.builder()
            .popRank(req.getPopRank())
            .category(req.getCategory())
            .name(req.getName())
            .manufacturer(req.getManufacturer())
            .price(req.getPrice())
            .status(req.getStatus())
            .image(req.getImage())
            .specs(req.getSpecs())
            .productLink(req.getProductLink())
            .description(req.getDescription())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    public Page<ProductResponse> getProductsByCategory(String category, String manufacturer, String sort, String keyword, Pageable pageable) {

        Pageable sortedPageable = createSortedPageable(pageable, sort);

        String cacheKey = "products:" +
            (category != null ? category : "all") + ":" +
            (manufacturer != null ? manufacturer : "all") + ":" +
            (keyword != null ? keyword : "") + ":" +
            (sort != null ? sort : "latest") + ":" +
            pageable.getPageNumber() + ":" + pageable.getPageSize();

        ValueOperations<String, Object> ops = redisTemplate.opsForValue();

        Object cached = ops.get(cacheKey);
        if (cached != null) {
            return (Page<ProductResponse>) cached;
        }

        Page<Product> products = productRepository.searchProducts(category, manufacturer, keyword, sortedPageable);

        Page<ProductResponse> response = products.map(this::toResponse);

        ops.set(cacheKey, response, 10, TimeUnit.MINUTES);

        return response;
    }

    private Pageable createSortedPageable(Pageable pageable, String sort) {
        Sort sortSpec;
        if (sort == null) {
            sortSpec = Sort.by("updatedAt").descending(); // 기본값: 최신순
        } else {
            switch (sort) {
                case "high":
                    sortSpec = Sort.by("price").descending();
                    break;
                case "low":
                    sortSpec = Sort.by("price").ascending();
                    break;
                case "latest":
                default:
                    sortSpec = Sort.by("updatedAt").descending();
                    break;
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortSpec);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public ProductResponse getProduct(Long id) {
        return productRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest req) {
        Product product = toEntity(req);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest req) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setPopRank(req.getPopRank());
        product.setCategory(req.getCategory());
        product.setName(req.getName());
        product.setManufacturer(req.getManufacturer());
        product.setPrice(req.getPrice());
        product.setStatus(req.getStatus());
        product.setImage(req.getImage());
        product.setSpecs(req.getSpecs());
        product.setProductLink(req.getProductLink());
        product.setDescription(req.getDescription());
        product.setUpdatedAt(OffsetDateTime.now());

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}