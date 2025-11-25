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
import specmate.backend.dto.estimate.ai.AiEstimateProductRequest;
import specmate.backend.dto.estimate.ai.AiEstimateProductResponse;
import specmate.backend.dto.estimate.ai.AiEstimateRequest;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.util.List;

@RestController
@RequestMapping("/api/aiestimates")
@RequiredArgsConstructor
@Tag(name = "AI Estimate API", description = "AI 견적(Estimate) CRUD 및 부품 관리 API")
public class AiEstimateController {

    private final AiEstimateService aiEstimateService;

    @Operation(summary = "AI 견적 생성", description = "새로운 AI 견적을 생성합니다. JWT 토큰에서 userId를 추출합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @PostMapping
    public ResponseEntity<AiEstimateResponse> createEstimate(@RequestBody AiEstimateRequest req, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        req.setUserId(userId);
        return ResponseEntity.ok(aiEstimateService.createEstimate(req));
    }

    @Operation(summary = "AI 견적에 제품 추가", description = "기존 AI 견적에 제품을 추가합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @PostMapping("/{estimateId}/products")
    public ResponseEntity<AiEstimateProductResponse> addProductToEstimate(@PathVariable Long estimateId, @RequestBody AiEstimateProductRequest req, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(aiEstimateService.addProductToEstimate(estimateId, req, userId));
    }

    @Operation(summary = "AI 견적 조회", description = "특정 AI 견적을 제품 목록과 함께 조회합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/{estimateId}")
    public ResponseEntity<AiEstimateResponse> getEstimate(@PathVariable Long estimateId, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(aiEstimateService.getEstimate(estimateId, userId));
    }

    @Operation(summary = "특정 AI 견적의 제품 조회", description = "특정 AI 견적에 담긴 모든 제품 조회", security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/{estimateId}/products")
    public ResponseEntity<List<AiEstimateProductResponse>> getEstimateProducts(@PathVariable Long estimateId) {
        return ResponseEntity.ok(aiEstimateService.getEstimateProducts(estimateId));
    }

    @Operation(summary = "내 AI 견적 목록 조회", description = "JWT 토큰에 포함된 userId 기준으로 모든 AI 견적 조회", security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/me")
    public ResponseEntity<List<AiEstimateResponse>> getMyEstimates(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(aiEstimateService.getUserEstimates(userId));
    }

    @Operation(summary = "AI 견적에서 제품 제거", description = "특정 AI 견적에 담긴 개별 제품을 제거합니다. 견적 총 가격도 함께 갱신됩니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> removeProductFromEstimate(@PathVariable Long productId, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        aiEstimateService.removeProductFromEstimate(productId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "AI 견적 삭제", description = "특정 AI 견적 삭제 (제품도 함께 삭제됨)", security = { @SecurityRequirement(name = "bearerAuth") },
            responses = {
                    @ApiResponse(responseCode = "204", description = "견적이 성공적으로 삭제됨"),
                    @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰이 유효하지 않음"),
                    @ApiResponse(responseCode = "403", description = "해당 견적의 삭제 권한이 없음"),
                    @ApiResponse(responseCode = "404", description = "해당 ID의 견적이 존재하지 않음")
            })
    @DeleteMapping("/{estimateId}")
    public ResponseEntity<Void> deleteEstimate(@PathVariable Long estimateId, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        aiEstimateService.deleteEstimate(estimateId, userId);
        return ResponseEntity.noContent().build();
    }
}
