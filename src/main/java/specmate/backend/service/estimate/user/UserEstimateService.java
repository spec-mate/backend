package specmate.backend.service.estimate.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
            .totalPrice(0L)
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
                    .totalPrice(0L)
                    .build();

                return userEstimateRepository.save(newEstimate);
            });
    }

    /** 특정 견적에 제품 추가 */
    @Transactional
    public UserEstimateProductResponse addProductToEstimate(String estimateId, UserEstimateProductRequest req, String userId) {
        UserEstimate estimate = userEstimateRepository.findById(estimateId)
            .orElseThrow(() -> new RuntimeException("Estimate not found"));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        Product product = productRepository.findById(Long.valueOf(req.getProductId()))
            .orElseThrow(() -> new RuntimeException("Product not found"));

        long unitPrice = product.getPrice() != null ? product.getPrice() : 0L;
        long totalPrice = unitPrice * req.getQuantity();

        UserEstimateProduct estimateProduct = UserEstimateProduct.builder()
            .userEstimate(estimate)
            .product(product)
            .category(req.getCategory())
            .quantity(req.getQuantity())
            .unitPrice(unitPrice)
            .totalPrice(totalPrice)
            .build();

        long currentTotal = estimate.getTotalPrice() != null ? estimate.getTotalPrice() : 0L;
        estimate.setTotalPrice(currentTotal + totalPrice);

        return toEstimateProductResponse(userEstimateProductRepository.save(estimateProduct));
    }

    /** 자동으로 견적 찾아 제품 추가 */
    @Transactional
    public UserEstimateProductResponse saveToMyEstimate(String userId, UserEstimateProductRequest req) {
        UserEstimate estimate = getOrCreateDefaultEstimate(userId);
        return addProductToEstimate(estimate.getId(), req, userId);
    }

    /** 견적에 포함된 제품들 조회 */
    public List<UserEstimateProductResponse> getEstimateProducts(String estimateId) {
        return userEstimateProductRepository.findByUserEstimateId(estimateId)
            .stream()
            .map(this::toEstimateProductResponse)
            .collect(Collectors.toList());
    }

    /** 특정 견적에서 제품 교체 */
    @Transactional
    public UserEstimateProductResponse replaceProductInEstimate(String estimateProductId, UserEstimateProductRequest req, String userId) {
        UserEstimateProduct estimateProduct = userEstimateProductRepository.findById(estimateProductId)
            .orElseThrow(() -> new RuntimeException("견적에 포함된 부품이 없습니다."));

        UserEstimate estimate = estimateProduct.getUserEstimate();

        if (!estimate.getUser().getId().equals(userId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        // 기존 제품 가격 차감
        long oldProductTotal = estimateProduct.getTotalPrice();
        long currentEstimateTotal = estimate.getTotalPrice() != null ? estimate.getTotalPrice() : 0L;

        // 새로운 제품 조회
        Product newProduct = productRepository.findById(Long.valueOf(req.getProductId()))
            .orElseThrow(() -> new RuntimeException("부품을 찾을 수 없습니다."));

        long newUnitPrice = newProduct.getPrice() != null ? newProduct.getPrice() : 0L;
        long newTotalPrice = newUnitPrice * req.getQuantity();

        // 제품 정보 업데이트
        estimateProduct.setProduct(newProduct);
        estimateProduct.setCategory(req.getCategory());
        estimateProduct.setQuantity(req.getQuantity());
        estimateProduct.setUnitPrice(newUnitPrice);
        estimateProduct.setTotalPrice(newTotalPrice);
        estimateProduct.calculateTotalPrice(); // 안전장치

        // 견적서 총액 재계산
        long updatedTotal = currentEstimateTotal - oldProductTotal + newTotalPrice;
        estimate.setTotalPrice(Math.max(updatedTotal, 0L));

        return toEstimateProductResponse(estimateProduct);
    }

    /** 유저의 모든 견적 조회 */
    public List<UserEstimateResponse> getUserEstimates(String userId) {
        return userEstimateRepository.findByUserId(userId)
            .stream()
            .map(this::toEstimateResponse)
            .collect(Collectors.toList());
    }

    /** 특정 견적에서 특정 제품 삭제 **/
    @Transactional
    public void removeProductFromEstimate(String estimateProductId, String userId) {
        UserEstimateProduct estimateProduct = userEstimateProductRepository.findById(estimateProductId)
            .orElseThrow(() -> new RuntimeException("Estimate product not found"));

        UserEstimate estimate = estimateProduct.getUserEstimate();

        if (!estimate.getUser().getId().equals(userId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        // 가격 차감
        long currentTotal = estimate.getTotalPrice() != null ? estimate.getTotalPrice() : 0L;
        long productTotal = estimateProduct.getTotalPrice();
        estimate.setTotalPrice(Math.max(currentTotal - productTotal, 0L));

        userEstimateProductRepository.delete(estimateProduct);
    }

    /** 견적 삭제 */
    @Transactional
    public void deleteEstimate(String estimateId, String userId) {
        UserEstimate estimate = userEstimateRepository.findById(estimateId)
            .orElseThrow(() -> new RuntimeException("Estimate not found"));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new RuntimeException("권한이 없습니다.");
        }

        userEstimateRepository.delete(estimate);
    }

    /** 변환 메서드 */
    private UserEstimateResponse toEstimateResponse(UserEstimate entity) {
        return UserEstimateResponse.builder()
            .id(entity.getId())
            .userId(entity.getUser().getId())
            .title(entity.getTitle())
            .description(entity.getDescription())
            .totalPrice(entity.getTotalPrice()) // Long
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    private UserEstimateProductResponse toEstimateProductResponse(UserEstimateProduct entity) {
        return UserEstimateProductResponse.builder()
            .id(entity.getId())
            .estimateId(entity.getUserEstimate().getId())
            .productId(entity.getProduct().getId()) // Long
            .productName(entity.getProduct().getName())
            .image(entity.getProduct().getImage())
            .category(entity.getCategory())
            .quantity(entity.getQuantity())
            .unitPrice(entity.getUnitPrice()) // Long
            .totalPrice(entity.getTotalPrice()) // Long
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}