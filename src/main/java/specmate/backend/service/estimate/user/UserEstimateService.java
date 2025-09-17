package specmate.backend.service.estimate.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.user.UserEstimateProductRequest;
import specmate.backend.dto.estimate.user.UserEstimateProductResponse;
import specmate.backend.dto.estimate.user.UserEstimateRequest;
import specmate.backend.dto.estimate.user.UserEstimateResponse;
import specmate.backend.entity.Product;
import specmate.backend.entity.User;
import specmate.backend.entity.UserEstimate;
import specmate.backend.entity.UserEstimateProduct;
import specmate.backend.repository.estimate.user.UserEstimateProductRepository;
import specmate.backend.repository.estimate.user.UserEstimateRepository;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserEstimateService {

    private final UserEstimateRepository userEstimateRepository;
    private final UserEstimateProductRepository userEstimateProductRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /** 견적 생성 */
    @Transactional
    public UserEstimateResponse createEstimate(UserEstimateRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserEstimate estimate = UserEstimate.builder()
                .user(user)
                .title(req.getTitle())
                .description(req.getDescription())
                .totalPrice(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return toEstimateResponse(userEstimateRepository.save(estimate));
    }

    /** 기본 견적 가져오거나 생성 */
    @Transactional
    public UserEstimate getOrCreateDefaultEstimate(String userId) {
        return userEstimateRepository.findByUserId(userId).stream()
                .findFirst()
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    UserEstimate newEstimate = UserEstimate.builder()
                            .user(user)
                            .title("내 견적")
                            .description("자동 생성된 견적")
                            .totalPrice(0)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return userEstimateRepository.save(newEstimate);
                });
    }

    /** 특정 견적에 제품 추가 */
    @Transactional
    public UserEstimateProductResponse addProductToEstimate(String estimateId, UserEstimateProductRequest req) {
        UserEstimate estimate = userEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new RuntimeException("Estimate not found"));

        Product product = productRepository.findById(Integer.valueOf(req.getProductId()))
                .orElseThrow(() -> new RuntimeException("Product not found"));

        int unitPrice = Integer.parseInt(product.getLowestPrice().get("price").toString().replace(",", ""));
        int totalPrice = unitPrice * req.getQuantity();

        LocalDateTime now = LocalDateTime.now();

        UserEstimateProduct estimateProduct = UserEstimateProduct.builder()
                .userEstimate(estimate)
                .product(product)
                .category(req.getCategory())
                .quantity(req.getQuantity())
                .unitPrice(unitPrice)
                .totalPrice(totalPrice)
                .createdAt(now)
                .updatedAt(now)
                .build();

        estimate.setTotalPrice((estimate.getTotalPrice() == null ? 0 : estimate.getTotalPrice()) + totalPrice);

        return toEstimateProductResponse(userEstimateProductRepository.save(estimateProduct));
    }

    /** 자동으로 견적 찾아 제품 추가 */
    @Transactional
    public UserEstimateProductResponse saveToMyEstimate(String userId, UserEstimateProductRequest req) {
        UserEstimate estimate = getOrCreateDefaultEstimate(userId);
        return addProductToEstimate(estimate.getId(), req);
    }

    /** 견적에 포함된 제품들 조회 */
    public List<UserEstimateProductResponse> getEstimateProducts(String estimateId) {
        return userEstimateProductRepository.findByUserEstimateId(estimateId)
                .stream()
                .map(this::toEstimateProductResponse)
                .collect(Collectors.toList());
    }

    /** 유저의 모든 견적 조회 */
    public List<UserEstimateResponse> getUserEstimates(String userId) {
        return userEstimateRepository.findByUserId(userId)
                .stream()
                .map(this::toEstimateResponse)
                .collect(Collectors.toList());
    }

    /** 견적 삭제 */
    @Transactional
    public void deleteEstimate(String estimateId) {
        userEstimateRepository.deleteById(estimateId);
    }

    /** 변환 메서드 */
    private UserEstimateResponse toEstimateResponse(UserEstimate entity) {
        return UserEstimateResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .totalPrice(entity.getTotalPrice())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private UserEstimateProductResponse toEstimateProductResponse(UserEstimateProduct entity) {
        return UserEstimateProductResponse.builder()
                .id(entity.getId())
                .estimateId(entity.getUserEstimate().getId())
                .productId(entity.getProduct().getId())
                .productName(entity.getProduct().getName())
                .category(entity.getCategory())
                .quantity(entity.getQuantity())
                .unitPrice(entity.getUnitPrice())
                .totalPrice(entity.getTotalPrice())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
