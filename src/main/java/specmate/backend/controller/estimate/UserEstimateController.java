package specmate.backend.controller.estimate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.estimate.user.UserEstimateProductRequest;
import specmate.backend.dto.estimate.user.UserEstimateProductResponse;
import specmate.backend.dto.estimate.user.UserEstimateRequest;
import specmate.backend.dto.estimate.user.UserEstimateResponse;
import specmate.backend.service.estimate.user.UserEstimateService;

import java.util.List;

@RestController
@RequestMapping("/api/estimate")
@RequiredArgsConstructor
@Tag(name = "User Estimate API", description = "사용자 견적(Estimate) CRUD 및 부품 관리 API")
public class UserEstimateController {

    private final UserEstimateService userEstimateService;

    @Operation(summary = "견적 생성", description = "새로운 사용자 견적을 생성합니다. JWT 토큰에서 userId를 추출합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @PostMapping
    public ResponseEntity<UserEstimateResponse> createEstimate(@RequestBody UserEstimateRequest req, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        req.setUserId(userId);
        return ResponseEntity.ok(userEstimateService.createEstimate(req));
    }

    @Operation(summary = "특정 견적에 제품 추가", description = "기존 견적에 제품을 추가합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @PostMapping("/{estimateId}/products")
    public ResponseEntity<UserEstimateProductResponse> addProductToEstimate(@PathVariable String estimateId, @RequestBody UserEstimateProductRequest req, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(userEstimateService.addProductToEstimate(estimateId, req, userId));
    }

    @Operation(summary = "내 견적 보관함에 저장", description = "견적이 없으면 생성 후, 해당 견적에 제품을 저장합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @PostMapping("/products/save")
    public ResponseEntity<UserEstimateProductResponse> saveToMyEstimate(@RequestBody UserEstimateProductRequest req, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(userEstimateService.saveToMyEstimate(userId, req));
    }

    @Operation(summary = "특정 견적의 제품 조회", description = "특정 견적에 담긴 모든 제품 조회", security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/{estimateId}/products")
    public ResponseEntity<List<UserEstimateProductResponse>> getEstimateProducts(@PathVariable String estimateId, Authentication authentication) {
        return ResponseEntity.ok(userEstimateService.getEstimateProducts(estimateId));
    }

    @Operation(summary = "견적에서 제품 제거", description = "특정 견적에 담긴 개별 제품을 제거합니다. 견적 총 가격도 함께 갱신됩니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @DeleteMapping("/products/{estimateProductId}")
    public ResponseEntity<Void> removeProductFromEstimate(@PathVariable String estimateProductId, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        userEstimateService.removeProductFromEstimate(estimateProductId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 견적 목록 조회", description = "JWT 토큰에 포함된 userId 기준으로 모든 견적 조회", security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/me")
    public ResponseEntity<List<UserEstimateResponse>> getMyEstimates(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(userEstimateService.getUserEstimates(userId));
    }

    @Operation(summary = "견적 삭제", description = "특정 견적 삭제 (제품도 함께 삭제됨)", security = { @SecurityRequirement(name = "bearerAuth") })
    @DeleteMapping("/{estimateId}")
    public ResponseEntity<Void> deleteEstimate(@PathVariable String estimateId, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        userEstimateService.deleteEstimate(estimateId, userId);
        return ResponseEntity.noContent().build();
    }
}