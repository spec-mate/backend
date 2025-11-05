package specmate.backend.service.estimate.ai;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.*;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.UserAction;
import specmate.backend.repository.chat.*;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;
import specmate.backend.service.embedding.ProductEmbeddingService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEstimateService {

    private final AiEstimateRepository aiEstimateRepository;
    private final EstimateProductRepository estimateProductRepository;
    private final UserRepository userRepository;
    private final ProductEmbeddingService productEmbeddingService;
    private final ProductRepository productRepository;

    /** AI가 자동 생성한 견적 저장 */
    @Transactional
    public AiEstimate createAiEstimate(ChatRoom room, ChatMessage assistantMsg, EstimateResult result, List<Map<String, Object>> ragData) {
        if (result == null)
            throw new IllegalArgumentException("EstimateResult가 null입니다.");

        // RAG 기반 실제 제품 매칭
        if (ragData != null && !ragData.isEmpty()) {
            List<EstimateResult.Product> matchedProducts = new ArrayList<>();
            for (String type : List.of("case", "cpu", "vga", "ram", "ssd", "power", "mainboard", "cooler", "hdd")) {
                Map<String, Object> bestMatch = findBestMatch(ragData, type);
                if (bestMatch != null) {
                    String name = (String) bestMatch.get("name");
                    String description = generateShortDescription(bestMatch);

                    String price = null;
                    String image = null;

                    // price와 image는 detail 내부에서 가져오기
                    Object detailObj = bestMatch.get("detail");
                    if (detailObj instanceof Map<?, ?> detailMap) {
                        price = String.valueOf(detailMap.get("price"));
                        image = String.valueOf(detailMap.get("image"));
                    }

                    // 혹시 detail에 없고 루트에 있는 경우 대비
                    if (price == null && bestMatch.get("price") != null)
                        price = String.valueOf(bestMatch.get("price"));
                    if (image == null && bestMatch.get("image") != null)
                        image = String.valueOf(bestMatch.get("image"));

                    EstimateResult.Product p = EstimateResult.Product.builder()
                            .type(type)
                            .name(name)
                            .description(description)
                            .detail(new EstimateResult.Detail(price, image))
                            .build();
                    matchedProducts.add(p);

                    log.info("[AI 견적 생성] type={}, name={}, price={}, image={}", type, name, price, image);
                }
            }
            result.setProducts(matchedProducts);
        }

        // total 계산
        int totalPrice = Optional.ofNullable(result.getProducts())
                .orElse(List.of())
                .stream()
                .mapToInt(p -> parsePrice(p.getDetail() != null ? p.getDetail().getPrice() : "0"))
                .sum();

        AiEstimate aiEstimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .message(assistantMsg)
                .title(Optional.ofNullable(result.getBuildName()).orElse("이름 없는 견적"))
                .totalPrice(totalPrice)
                .status("SUCCESS")
                .userAction(UserAction.NONE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        aiEstimateRepository.save(aiEstimate);

        // EstimateProduct 저장
        saveEstimateProducts(aiEstimate, result);

        log.info("AI 자동 견적 저장 완료: {} (총액 {}원)", aiEstimate.getTitle(), totalPrice);
        return aiEstimate;
    }

    private Map<String, Object> findBestMatch(List<Map<String, Object>> ragData, String type) {
        return ragData.stream()
                .filter(p -> type.equals(p.get("type")))
                .findFirst()
                .orElse(null);
    }

    private String generateShortDescription(Map<String, Object> product) {
        String name = (String) product.get("name");
        return name != null && name.length() > 12 ? name.substring(0, 12) : name;
    }

    /** 견적 재구성 */
    @Transactional
    public AiEstimate reconstructAiEstimate(ChatRoom room, User user, ChatMessage assistantMsg,
                                            EstimateResult existingResult, List<Map<String, Object>> ragData) {
        if (existingResult == null)
            throw new IllegalArgumentException("기존 견적 없음");

        List<EstimateResult.Product> updatedComponents = new ArrayList<>();
        for (EstimateResult.Product p : existingResult.getProducts()) {
            if (shouldReplace(p.getType(), assistantMsg.getContent())) {
                Map<String, Object> bestMatch = findBestMatch(ragData, p.getType());
                if (bestMatch != null) {
                    String name = (String) bestMatch.get("name");
                    String price = String.valueOf(bestMatch.get("price"));
                    String image = (String) bestMatch.get("image");

                    p = EstimateResult.Product.builder()
                            .type(p.getType())
                            .name(name)
                            .description(generateShortDescription(bestMatch))
                            .detail(new EstimateResult.Detail(price, image))
                            .build();
                }
            }
            updatedComponents.add(p);
        }

        existingResult.setProducts(updatedComponents);
        Integer totalPrice = updatedComponents.stream()
                .mapToInt(prod -> parsePrice(prod.getDetail() != null ? prod.getDetail().getPrice() : "0"))
                .sum();

        AiEstimate aiEstimate = AiEstimate.builder()
                .chatRoom(room)
                .user(user)
                .message(assistantMsg)
                .title(existingResult.getBuildName())
                .totalPrice(totalPrice)
                .status("SUCCESS")
                .userAction(UserAction.NONE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        aiEstimateRepository.save(aiEstimate);
        saveEstimateProducts(aiEstimate, existingResult);

        return aiEstimate;
    }

    private boolean shouldReplace(String type, String userInput) {
        return userInput != null && userInput.toLowerCase().contains(type);
    }

    /** 사용자가 직접 저장한 견적 생성 */
    @Transactional
    public UserSavedEstimateResponse createUserSaveAiEstimate(String userId, UserSavedEstimateRequest request) {
        if (request == null)
            throw new IllegalArgumentException("요청 데이터가 비어 있습니다.");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // totalPrice 계산
        int totalPrice = request.getComponents() != null
                ? request.getComponents().stream()
                .mapToInt(c -> parsePrice(c.getDetail() != null ? c.getDetail().getPrice() : "0"))
                .sum()
                : 0;

        // 제목 우선순위: 1. 사용자가 지정한 제목 → 2. AI 견적명(buildName) → 3. 기본값
        String title = Optional.ofNullable(request.getTitle())
                .filter(t -> !t.isBlank())
                .or(() -> Optional.ofNullable(request.getBuildName()))
                .orElse("자동 생성 견적");

        AiEstimate aiEstimate = AiEstimate.builder()
                .user(user)
                .title(title)
                .description(request.getDescription())
                .totalPrice(totalPrice)
                .status("SUCCESS")
                .userAction(UserAction.SAVED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        aiEstimateRepository.save(aiEstimate);

        if (request.getComponents() != null && !request.getComponents().isEmpty()) {
            EstimateResult result = convertUserRequestToResult(request);
            saveEstimateProducts(aiEstimate, result);
        }

        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(aiEstimate.getId());
        return UserSavedEstimateResponse.fromEntity(aiEstimate, products);
    }


    /** 견적 제품 저장 */
    @Transactional
    public void saveEstimateProducts(AiEstimate aiEstimate, EstimateResult result) {
        if (result == null || result.getProducts() == null) return;

        // RAG 검색 결과 기반 매칭
        List<EstimateResult.Product> matches = productEmbeddingService.matchAiResponseToProducts(result);

        for (EstimateResult.Product match : matches) {
            EstimateResult.Product aiProd = result.getProducts().stream()
                    .filter(p -> p.getType().equalsIgnoreCase(match.getType()))
                    .findFirst()
                    .orElse(null);

            if (aiProd == null) continue;

            Product matchedProduct = null;

            try {
                // DB Product 조회
                if (match.getProductId() != null) {
                    matchedProduct = productRepository.findById(match.getProductId().intValue())
                            .orElse(null);
                } else if (match.getId() != null && !match.getId().isBlank()) {
                    long productId = Long.parseLong(match.getId());
                    matchedProduct = productEmbeddingService.findProductById(productId);
                }
            } catch (Exception e) {
                log.warn("Product 조회 실패 (ID={}): {}", match.getId(), e.getMessage());
            }

            // EstimateProduct 생성 및 저장
            EstimateProduct entity = EstimateProduct.builder()
                    .aiEstimate(aiEstimate)
                    .aiName(aiProd.getAiName())
                    .matchedName(aiProd.getName())
                    .type(aiProd.getType())
                    .description(aiProd.getDescription())
                    .image(aiProd.getDetail() != null ? aiProd.getDetail().getImage() : "")
                    .unitPrice(parsePrice(aiProd.getDetail() != null ? aiProd.getDetail().getPrice() : "0"))
                    .matched(!Objects.equals(aiProd.getName(), "미선택"))
                    .quantity(1)
                    .similarityScore(1.0)
                    .createdAt(LocalDateTime.now())
                    .build();

            if (matchedProduct != null) {
                entity.setProduct(matchedProduct);
            }

            estimateProductRepository.save(entity);
        }
    }

    /** 마이페이지: 사용자가 저장한 견적만 조회 */
    @Transactional
    public List<AiEstimateResponse> getEstimatesByUser(String userId) {
        return aiEstimateRepository.findByUserIdAndUserAction(userId, UserAction.SAVED)
                .stream()
                .map(estimate -> {
                    List<EstimateProduct> products =
                            estimateProductRepository.findAllByAiEstimateId(estimate.getId());
                    return AiEstimateResponse.fromEntityWithProducts(estimate, products);
                })
                .collect(Collectors.toList());
    }

    /** 특정 견적 상세 조회 */
    @Transactional
    public AiEstimateResponse getEstimateWithProducts(String estimateId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 견적입니다."));
        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(estimateId);
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);
    }

    /** 사용자 반응 업데이트 */
    @Transactional
    public AiEstimateResponse updateUserAction(String aiEstimateId, UserAction action, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(aiEstimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 AI 견적입니다."));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 견적에 대한 권한이 없습니다.");
        }

        if (action == null) {
            throw new IllegalArgumentException("UserAction 값이 없습니다.");
        }

        estimate.setUserAction(action);
        estimate.setUpdatedAt(LocalDateTime.now());
        aiEstimateRepository.save(estimate);

        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(aiEstimateId);
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);
    }

    /** 견적 삭제 */
    @Transactional
    public void deleteAiEstimate(String estimateId, String userId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("AI 견적을 찾을 수 없습니다."));

        if (!estimate.getUser().getId().equals(userId)) {
            throw new SecurityException("삭제 권한이 없습니다.");
        }

        estimateProductRepository.deleteAllByAiEstimateId(estimate.getId());
        aiEstimateRepository.delete(estimate);
        log.info("AI 견적({}) 및 연결된 제품이 삭제되었습니다.", estimateId);
    }

    /** 가격 파싱 유틸 */
    private Integer parsePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            log.warn("가격 파싱 실패: {}", priceStr);
            return 0;
        }
    }

    /** 유저 요청 변환 */
    public EstimateResult convertUserRequestToResult(UserSavedEstimateRequest req) {
        List<EstimateResult.Product> products = req.getComponents().stream()
                .map(c -> EstimateResult.Product.builder()
                        .id(null)
                        .type(c.getType())
                        .name(c.getName())
                        .description(c.getDescription())
                        .detail(new EstimateResult.Detail(
                                c.getDetail() != null ? c.getDetail().getPrice() : "0",
                                c.getDetail() != null ? c.getDetail().getImage() : ""
                        ))
                        .build())
                .collect(Collectors.toList());
        return EstimateResult.builder().products(products).build();
    }
}
