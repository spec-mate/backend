package specmate.backend.controller.estimate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.aiestimate.AiEstimateResponse;
import specmate.backend.dto.aiestimate.AiEstimateUpdateRequest;
import specmate.backend.service.estimate.ai.AiEstimateService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-estimates")
@RequiredArgsConstructor
@Tag(name = "AI Estimate", description = "AI 추천 견적 API")
public class AiEstimateController {

    private final AiEstimateService aiEstimateService;

    @Operation(summary = "AI 견적 조회", description = "특정 채팅방에서 생성된 모든 AI 추천 견적을 조회합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<List<AiEstimateResponse>> getEstimates(@PathVariable UUID roomId) {
        return ResponseEntity.ok(aiEstimateService.getEstimatesByRoomId(roomId));
    }

    @Operation(summary = "AI 견적 수정", description = "AI 추천 견적의 빌드 이름이나 설명을 수정합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @PatchMapping("/{estimateId}")
    public ResponseEntity<AiEstimateResponse> updateEstimate(
            @PathVariable String estimateId,
            @RequestBody AiEstimateUpdateRequest request
    ) {
        return ResponseEntity.ok(aiEstimateService.updateEstimate(estimateId, request));
    }


    @Operation(summary = "AI 견적 삭제", description = "AI 추천 견적을 삭제합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @DeleteMapping("/{estimateId}")
    public ResponseEntity<Void> deleteEstimate(@PathVariable String estimateId) {
        aiEstimateService.deleteEstimate(estimateId);
        return ResponseEntity.noContent().build();
    }
}
