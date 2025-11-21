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
            .name(product.getName())
            .brand(product.getBrand())
            .category(product.getCategory())
            .image(product.getImage())
            .transparentImage(product.getTransparentImage())
            .priceUsd(product.getPriceUsd())
            .priceKrw(product.getPriceKrw())
            .availability(product.getAvailability())
            .productLink(product.getProductLink())
            .updatedAt(product.getUpdatedAt())
            .detail(product.getDetail())
            .description(product.getDescription())
            .build();
    }

    private Product toEntity(ProductRequest req) {
        return Product.builder()
            .name(req.getName())
            .brand(req.getBrand())
            .category(req.getCategory())
            .image(req.getImage())
            .transparentImage(req.getTransparentImage())
            .priceUsd(req.getPriceUsd())
            .priceKrw(req.getPriceKrw())
            .availability(req.getAvailability())
            .productLink(req.getProductLink())
            .detail(req.getDetail())
            .description(req.getDescription())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    public Page<ProductResponse> getProductsByCategory(String category, String brand, String sort, String keyword, Pageable pageable) {

        Pageable sortedPageable = createSortedPageable(pageable, sort);

        String cacheKey = "products:" +
            (category != null ? category : "all") + ":" +
            (brand != null ? brand : "all") + ":" +
            (keyword != null ? keyword : "") + ":" +
            (sort != null ? sort : "latest") + ":" +
            pageable.getPageNumber() + ":" + pageable.getPageSize();

        ValueOperations<String, Object> ops = redisTemplate.opsForValue();

        Object cached = ops.get(cacheKey);
        if (cached != null) {
            return (Page<ProductResponse>) cached;
        }

        Page<Product> products = productRepository.searchProducts(category, brand, keyword, sortedPageable);

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
                    sortSpec = Sort.by("priceKrw").descending();
                    break;
                case "low":
                    sortSpec = Sort.by("priceKrw").ascending();
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

        product.setName(req.getName());
        product.setBrand(req.getBrand());
        product.setCategory(req.getCategory());
        product.setImage(req.getImage());
        product.setTransparentImage(req.getTransparentImage());
        product.setPriceUsd(req.getPriceUsd());
        product.setPriceKrw(req.getPriceKrw());
        product.setAvailability(req.getAvailability());
        product.setProductLink(req.getProductLink());
        product.setDetail(req.getDetail());
        product.setDescription(req.getDescription());
        product.setUpdatedAt(OffsetDateTime.now());

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}