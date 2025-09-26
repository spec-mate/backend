package specmate.backend.service.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private Product toEntity(ProductRequest req) {
        return Product.builder()
                .name(req.getName())
                .image(req.getImage())
                .popRank(req.getPopRank())
                .regDate(req.getRegDate())
                .options(req.getOptions())
                .priceInfo(req.getPriceInfo())
                .lowestPrice(req.getLowestPrice())
                .type(req.getType())
                .manufacturer(req.getManufacturer())
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

    public ProductResponse createProduct(ProductRequest req) {
        Product product = toEntity(req);
        return toResponse(productRepository.save(product));
    }

    public ProductResponse updateProduct(Integer id, ProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setName(req.getName());
        product.setImage(req.getImage());
        product.setPopRank(req.getPopRank());
        product.setRegDate(req.getRegDate());
        product.setOptions(req.getOptions());
        product.setPriceInfo(req.getPriceInfo());
        product.setLowestPrice(req.getLowestPrice());
        product.setType(req.getType());
        product.setManufacturer(req.getManufacturer());

        return toResponse(productRepository.save(product));
    }

    public void deleteProduct(Integer id) {
        productRepository.deleteById(id);
    }
}