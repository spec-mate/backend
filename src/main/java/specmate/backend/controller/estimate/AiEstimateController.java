package specmate.backend.controller.estimate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.util.List;

@RestController
@RequestMapping("/api/estimates")
@RequiredArgsConstructor
@Tag(
        name = "AI Estimate API",
        description = "AI가 생성한 컴퓨터 견적(AiEstimate) 및 구성된 상품(EstimateProduct)을 조회, 관리하는 API"
)
public class AiEstimateController {

    private final AiEstimateService aiEstimateService;

    /** 마이페이지 - 내 견적 목록 조회 */
    @Operation(
            summary = "내 견적 목록 조회",
            description = """
                    로그인한 사용자의 모든 AI 견적 목록을 조회합니다.<br>
                    각 견적에는 제목, 총 금액, 생성일, 상태 등이 포함됩니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AiEstimateResponse.class)))
                    ),
                    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)"),
                    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
            }
    )
    @GetMapping("/me")
    public ResponseEntity<List<AiEstimateResponse>> getMyEstimates(
            @Parameter(hidden = true) Authentication authentication
    ) {
        String userId = authentication.getName();
        List<AiEstimateResponse> estimates = aiEstimateService.getEstimatesByUser(userId);
        return ResponseEntity.ok(estimates);
    }

     /** 단일 견적 상세 조회 */
    @Operation(
            summary = "AI 견적 상세 조회",
            description = """
                    특정 AI 견적의 상세 정보를 조회합니다.<br>
                    견적 제목, 총 금액, 생성일, 설명(notes)과 함께 구성된 모든 상품 리스트를 반환합니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = AiEstimateResponse.class))
                    ),
                    @ApiResponse(responseCode = "400", description = "잘못된 견적 ID 형식"),
                    @ApiResponse(responseCode = "404", description = "존재하지 않는 견적"),
                    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)")
            }
    )
    @GetMapping("/{estimateId}")
    public ResponseEntity<AiEstimateResponse> getEstimateDetail(
            @Parameter(description = "조회할 AI 견적의 ID", example = "8f1b2cda-4b6e-478c-bf54-b9eecbe9d02e")
            @PathVariable String estimateId
    ) {
        AiEstimateResponse response = aiEstimateService.getEstimateWithProducts(estimateId);
        return ResponseEntity.ok(response);
    }


    /** 견적 삭제 */
    @Operation(
            summary = "AI 견적 삭제",
            description = """
                    특정 AI 견적을 삭제합니다.<br>
                    연결된 EstimateProduct(견적 내 제품) 데이터도 함께 삭제됩니다.<br>
                    사용자는 자신의 견적만 삭제할 수 있습니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)"),
                    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)"),
                    @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (본인 견적이 아님)"),
                    @ApiResponse(responseCode = "404", description = "해당 견적을 찾을 수 없음")
            }
    )
    @DeleteMapping("/{estimateId}")
    public ResponseEntity<Void> deleteEstimate(
            @Parameter(description = "삭제할 AI 견적의 ID", example = "8f1b2cda-4b6e-478c-bf54-b9eecbe9d02e")
            @PathVariable String estimateId,
            @Parameter(hidden = true) Authentication authentication
    ) {
        String userId = authentication.getName();
        aiEstimateService.deleteAiEstimate(estimateId, userId);
        return ResponseEntity.noContent().build();
    }
}
