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
import specmate.backend.dto.estimate.ai.AiEstimateRequest;
import specmate.backend.dto.estimate.ai.AiEstimateResponse;
import specmate.backend.dto.estimate.ai.UserSavedEstimateRequest;
import specmate.backend.dto.estimate.ai.UserSavedEstimateResponse;
import specmate.backend.entity.enums.UserAction;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.util.List;

@RestController
@RequestMapping("/api/aiestimates")
@RequiredArgsConstructor
@Tag(
        name = "AI Estimate API",
        description = """
            AI가 생성한 컴퓨터 견적(AiEstimate) 및 구성된 상품(EstimateProduct)을 조회, 관리하는 API
            """
)
public class AiEstimateController {

    private final AiEstimateService aiEstimateService;

    /**
     * 마이페이지 - 내 견적 목록 조회
     */
    @Operation(
            summary = "내 견적 목록 조회",
            description = """
                    로그인한 사용자의 모든 AI 견적 목록을 조회합니다.<br>
                    각 견적에는 제목, 총 금액, 생성일, 상태(status: SUCCESS, SAVED) 및 user_action이 포함됩니다.
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

    /**
     * 단일 견적 상세 조회
     */
    @Operation(
            summary = "AI 견적 상세 조회",
            description = """
                    특정 AI 견적의 상세 정보를 조회합니다.<br>
                    견적 제목, 총 금액, 생성일, 설명과 함께 구성된 모든 상품 리스트를 반환합니다.
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
    @GetMapping("/{aiestimateId}")
    public ResponseEntity<AiEstimateResponse> getEstimateDetail(
            @Parameter(description = "조회할 AI 견적의 ID", example = "8f1b2cda-4b6e-478c-bf54-b9eecbe9d02e")
            @PathVariable String aiestimateId
    ) {
        AiEstimateResponse response = aiEstimateService.getEstimateWithProducts(aiestimateId);
        return ResponseEntity.ok(response);
    }

    /**
     * AI 견적 사용자 업데이트
     */
    @PatchMapping("/{aiestimateId}/action")
    @Operation(
            summary = "AI 견적 사용자 반응 업데이트",
            description = """
                    사용자가 견적에 대해 취한 행동을 업데이트합니다.<br>
                    예를 들어, 다음과 같은 값으로 user_action을 변경할 수 있습니다:<br>
                    - <b>SAVED</b>: 보관함에 저장<br>
                    - <b>NOT_SAVED</b>: 저장하지 않음<br>
                    - <b>RETRY</b>: 재추천 요청<br>
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "업데이트 성공",
                            content = @Content(schema = @Schema(implementation = AiEstimateResponse.class))),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청 값"),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "403", description = "권한 없음"),
                    @ApiResponse(responseCode = "404", description = "해당 견적을 찾을 수 없음")
            }
    )
    public ResponseEntity<AiEstimateResponse> updateUserAction(
            @Parameter(description = "AI 견적 ID") @PathVariable String aiestimateId,
            @Parameter(description = "사용자 행동", example = "SAVED") @RequestParam UserAction action,
            @Parameter(hidden = true) Authentication authentication
    ) {
        String userId = authentication.getName();
        AiEstimateResponse updated = aiEstimateService.updateUserAction(aiestimateId, action, userId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 견적 삭제
     */
    @Operation(
            summary = "AI 견적 삭제",
            description = """
                    특정 AI 견적을 삭제합니다.<br>
                    연결된 AiEstimateProduct(견적 내 AI 제품) 데이터도 함께 삭제됩니다.<br>
                    사용자는 자신의 견적만 삭제할 수 있습니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)"),
                    @ApiResponse(responseCode = "403", description = "삭제 권한 없음 (본인 견적이 아님)"),
                    @ApiResponse(responseCode = "404", description = "해당 견적을 찾을 수 없음")
            }
    )
    @DeleteMapping("/{aiestimateId}")
    public ResponseEntity<Void> deleteEstimate(
            @Parameter(description = "삭제할 AI 견적의 ID", example = "8f1b2cda-4b6e-478c-bf54-b9eecbe9d02e")
            @PathVariable String aiestimateId,
            @Parameter(hidden = true) Authentication authentication
    ) {
        String userId = authentication.getName();
        aiEstimateService.deleteAiEstimate(aiestimateId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 사용자가 직접 보관함에 저장하는 AI 견적 생성
     */
    @PostMapping
    @Operation(
            summary = "사용자가 직접 보관함에 저장하는 AI 견적 생성",
            description = """
                    ChatPage에서 '보관함으로 이동' 버튼 클릭 시 호출됩니다.<br>
                    프론트에서 ai_estimate_id, title, description, total, components를 전달합니다.<br>
                    DB에는 AiEstimate 및 EstimateProduct가 함께 저장됩니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")}
    )
    public ResponseEntity<UserSavedEstimateResponse> createUserSaveEstimate(
            @Parameter(hidden = true) Authentication authentication,
            @RequestBody UserSavedEstimateRequest request
    ) {
        String userId = authentication.getName();
        UserSavedEstimateResponse response =
                aiEstimateService.createUserSaveAiEstimate(userId, request);
        return ResponseEntity.ok(response);
    }
}


