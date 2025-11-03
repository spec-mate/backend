package specmate.backend.service.estimate.ai;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.entity.*;
import specmate.backend.entity.enums.UserAction;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.repository.chat.ChatMessageRepository;
import specmate.backend.repository.chat.EstimateProductRepository;
import specmate.backend.repository.product.ProductRepository;
import specmate.backend.repository.user.UserRepository;
import specmate.backend.repository.chat.ChatRoomRepository;
import specmate.backend.service.product.ProductSearchService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEstimateService {

    private final EstimateProductRepository estimateProductRepository;
    private final AiEstimateRepository aiEstimateRepository;
    private final ProductSearchService productSearchService;

    /** AI가 자동 생성한 견적 저장 */
    @Transactional
    public AiEstimate createAiEstimate(ChatRoom room, ChatMessage assistantMsg, EstimateResult result, List<Product> qdrantProducts) {
        if (result == null) {
            throw new IllegalArgumentException("EstimateResult가 null입니다.");
        }

        AiEstimate aiEstimate = AiEstimate.builder()
                .chatRoom(room)
                .user(room.getUser())
                .message(assistantMsg)
                .title(Optional.ofNullable(result.getBuildName()).orElse("이름 없는 견적"))
                .totalPrice(parsePrice(result.getTotalPrice()))
                .status("SUCCESS")
                .userAction(UserAction.NONE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        aiEstimateRepository.save(aiEstimate);
        saveEstimateProducts(aiEstimate, result, qdrantProducts);

        return aiEstimate;
    }

    /** 제품 매핑 (자동 저장 시 사용) - Qdrant 검색 결과 사용 */
    @Transactional
    public void saveEstimateProducts(AiEstimate aiEstimate, EstimateResult result, List<Product> qdrantProducts) {
        if (result == null || result.getProducts() == null) return;

        log.info("Qdrant 제품 매칭 시작: {} 개 제품 풀에서 매칭 수행", qdrantProducts.size());

        for (EstimateResult.Product comp : result.getProducts()) {
            if (comp.getMatchedName() == null || comp.getMatchedName().isBlank()) continue;

            // Qdrant 결과에서 제품 매칭 (이름 기반 유사도)
            Optional<Product> matchedProduct = findBestMatchFromQdrant(
                    comp.getMatchedName(),
                    comp.getType(),
                    qdrantProducts
            );

            if (matchedProduct.isPresent()) {
                Product product = matchedProduct.get();
                log.info("Qdrant 매칭 성공: '{}' → '{}' (type={})",
                        comp.getMatchedName(), product.getName(), product.getType());

                saveEstimateProduct(aiEstimate, product, comp, 1.0);
            } else {
                // 매칭 실패 시 에러 로깅 (product_id NOT NULL 제약으로 저장 불가)
                log.error("❌ Qdrant 매칭 실패 - 제품 저장 불가: aiName='{}', type='{}', Qdrant 제품 풀 크기={}",
                        comp.getMatchedName(), comp.getType(), qdrantProducts.size());

                // Qdrant 제품 풀의 해당 타입 제품 목록 출력
                List<String> availableProducts = qdrantProducts.stream()
                        .filter(p -> p.getType().equalsIgnoreCase(comp.getType()))
                        .map(Product::getName)
                        .collect(Collectors.toList());

                log.error("   사용 가능한 '{}' 타입 제품: {}", comp.getType(), availableProducts);

                // DB 제약으로 인해 저장하지 않음 (product_id NOT NULL)
                // TODO: EstimateProduct.product_id를 nullable로 변경하면 매칭 실패 제품도 저장 가능
            }
        }
    }

    /**
     * Qdrant 결과에서 가장 유사한 제품 찾기
     */
    private Optional<Product> findBestMatchFromQdrant(String aiName, String type, List<Product> qdrantProducts) {
        if (qdrantProducts == null || qdrantProducts.isEmpty()) {
            log.warn("Qdrant 제품 풀이 비어있음");
            return Optional.empty();
        }

        if (aiName == null || aiName.isBlank()) {
            log.warn("AI 제품명이 비어있음");
            return Optional.empty();
        }

        // 정규화: 소문자 변환, 공백/특수문자 제거
        String normalizedAiName = normalizeProductName(aiName);

        log.debug("제품 매칭 시도: aiName='{}', normalized='{}', type='{}'", aiName, normalizedAiName, type);

        // 타입 일치하는 제품만 필터링
        List<Product> candidateProducts = qdrantProducts.stream()
                .filter(p -> p.getType().equalsIgnoreCase(type))
                .collect(Collectors.toList());

        if (candidateProducts.isEmpty()) {
            log.warn("타입 '{}' 제품이 Qdrant 결과에 없음", type);
            return Optional.empty();
        }

        log.debug("타입 '{}' 후보 제품: {}", type,
                candidateProducts.stream().map(Product::getName).collect(Collectors.toList()));

        // 1. 정규화된 이름이 정확히 일치
        Optional<Product> exactMatch = candidateProducts.stream()
                .filter(p -> normalizeProductName(p.getName()).equals(normalizedAiName))
                .findFirst();

        if (exactMatch.isPresent()) {
            log.info("정확 매칭 성공: '{}' → '{}'", aiName, exactMatch.get().getName());
            return exactMatch;
        }

        // 2. 정규화된 이름에 포함 (양방향)
        Optional<Product> containsMatch = candidateProducts.stream()
                .filter(p -> {
                    String normalizedDbName = normalizeProductName(p.getName());
                    return normalizedDbName.contains(normalizedAiName) ||
                            normalizedAiName.contains(normalizedDbName);
                })
                .findFirst();

        if (containsMatch.isPresent()) {
            log.info("포함 매칭 성공: '{}' → '{}'", aiName, containsMatch.get().getName());
            return containsMatch;
        }

        // 3. 주요 키워드 매칭 (공백으로 분리된 단어 중 3개 이상 일치)
        String[] aiNameTokens = normalizedAiName.split("\\s+");
        if (aiNameTokens.length >= 2) {
            Optional<Product> keywordMatch = candidateProducts.stream()
                    .filter(p -> {
                        String normalizedDbName = normalizeProductName(p.getName());
                        long matchCount = Arrays.stream(aiNameTokens)
                                .filter(token -> token.length() > 2) // 3글자 이상 단어만
                                .filter(normalizedDbName::contains)
                                .count();
                        return matchCount >= Math.min(2, aiNameTokens.length); // 최소 2개 또는 전체의 절반
                    })
                    .findFirst();

            if (keywordMatch.isPresent()) {
                log.info("키워드 매칭 성공: '{}' → '{}'", aiName, keywordMatch.get().getName());
                return keywordMatch;
            }
        }

        // 4. 타입 일치하는 첫 번째 제품 (최후 수단)
        Optional<Product> fallback = candidateProducts.stream().findFirst();
        if (fallback.isPresent()) {
            log.warn("폴백 매칭 (타입만 일치): '{}' → '{}'", aiName, fallback.get().getName());
            return fallback;
        }

        log.error("매칭 완전 실패: aiName='{}', type='{}', 후보 제품 수={}", aiName, type, candidateProducts.size());
        return Optional.empty();
    }

    /**
     * 제품명 정규화 (매칭용)
     */
    private String normalizeProductName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replaceAll("[\\s\\-_()\\[\\]]+", " ")  // 특수문자를 공백으로
                .replaceAll("\\s+", " ")                 // 연속 공백 제거
                .trim();
    }

    private void saveEstimateProduct(AiEstimate aiEstimate, Product product, EstimateResult.Product comp, double similarityScore) {

        // GPT가 제안한 이름
        String aiSuggestedName = comp.getMatchedName();

        // DB 매칭된 실제 이름
        String matchedProductName = product != null ? product.getName() : null;

        // DB 매칭된 이미지 (없으면 AI가 제공한 이미지 사용)
        String imageUrl = product != null && product.getImage() != null
                ? product.getImage()
                : comp.getImage();

        EstimateProduct entity = EstimateProduct.builder()
                .aiEstimate(aiEstimate)
                .product(product)
                .aiName(aiSuggestedName)
                .matchedName(matchedProductName)
                .type(comp.getType())
                .description(comp.getDescription())
                .image(imageUrl)
                .similarityScore(similarityScore)
                .matched(product != null)
                .quantity(1)
                .unitPrice(parsePrice(comp.getPrice()))
                .createdAt(LocalDateTime.now())
                .build();

        estimateProductRepository.save(entity);
    }

    /** 사용자 행동(user_action) 업데이트 */
    @Transactional
    public AiEstimateResponse updateUserAction(String aiEstimateId, UserAction action, String userId) {
        // 견적 조회
        AiEstimate estimate = aiEstimateRepository.findById(aiEstimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 AI 견적입니다."));

        // 권한 검증
        if (!estimate.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 견적에 대한 권한이 없습니다.");
        }

        // Enum 검증
        if (action == null) {
            throw new IllegalArgumentException("UserAction 값이 없습니다.");
        }

        // 상태 업데이트
        estimate.setUserAction(action);
        estimate.setUpdatedAt(LocalDateTime.now());

        aiEstimateRepository.save(estimate);

        // 응답 생성
        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(aiEstimateId);
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);
    }

    /** 조회, 삭제 로직 */
    @Transactional
    public List<AiEstimateResponse> getEstimatesByUser(String userId) {
        return aiEstimateRepository.findByUserId(userId)
                .stream()
                .map(estimate -> {
                    List<EstimateProduct> products =
                            estimateProductRepository.findAllByAiEstimateId(estimate.getId());
                    return AiEstimateResponse.fromEntityWithProducts(estimate, products);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public AiEstimateResponse getEstimateWithProducts(String estimateId) {
        AiEstimate estimate = aiEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 견적입니다."));
        List<EstimateProduct> products = estimateProductRepository.findAllByAiEstimateId(estimateId);
        return AiEstimateResponse.fromEntityWithProducts(estimate, products);
    }

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
        if (priceStr == null) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            log.warn("가격 파싱 실패: {}", priceStr);
            return 0;
        }
    }
}
