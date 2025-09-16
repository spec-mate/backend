package specmate.backend.service.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import specmate.backend.dto.product.ProductRequest;
import specmate.backend.dto.product.ProductResponse;
import specmate.backend.entity.Product;
import specmate.backend.repository.product.ProductRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

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

    public Page<ProductResponse> getProductsByType(String type, String manufacturer, String sort, Pageable pageable) {
        String cacheKey = "productsByType:" + type + ":" +
                (manufacturer != null ? manufacturer : "") + ":" +
                sort + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();

        ValueOperations<String, Object> ops = redisTemplate.opsForValue();

        Object cached = ops.get(cacheKey);
        if (cached != null) {
            System.out.println("[REDIS HIT] " + cacheKey);
            return (Page<ProductResponse>) cached;
        }

        Page<Product> products;
        boolean hasManufacturer = (manufacturer != null && !manufacturer.isEmpty());

        if (hasManufacturer) {
            if ("priceAsc".equalsIgnoreCase(sort)) {
                products = productRepository.findByTypeAndManufacturerOrderByLowestPriceAsc(type, manufacturer, pageable);
            } else if ("priceDesc".equalsIgnoreCase(sort)) {
                products = productRepository.findByTypeAndManufacturerOrderByLowestPriceDesc(type, manufacturer, pageable);
            } else {
                products = productRepository.findByTypeAndManufacturerOrderByPopRankAsc(type, manufacturer, pageable);
            }
        } else {
            if ("priceAsc".equalsIgnoreCase(sort)) {
                products = productRepository.findByTypeOrderByLowestPriceAsc(type, pageable);
            } else if ("priceDesc".equalsIgnoreCase(sort)) {
                products = productRepository.findByTypeOrderByLowestPriceDesc(type, pageable);
            } else {
                products = productRepository.findByTypeOrderByPopRankAsc(type, pageable);
            }
        }

        Page<ProductResponse> response = products.map(this::toResponse);

        ops.set(cacheKey, response, 10, TimeUnit.MINUTES);

        return response;
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