package specmate.backend.controller.estimate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.aiestimate.AiEstimateRequest;
import specmate.backend.dto.aiestimate.AiEstimateResponse;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.service.estimate.ai.AiEstimateService;
import specmate.backend.service.chat.ChatService;
import specmate.backend.repository.chat.ChatRoomRepository;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/ai-estimates")
@RequiredArgsConstructor
@Tag(name = "AI Estimate", description = "AI 추천 견적 API")
public class AiEstimateController {

    private final ChatService chatService;
    private final AiEstimateService aiEstimateService;
    private final ChatRoomRepository chatRoomRepository;

    @Operation(summary = "AI 견적 생성",
            description = "유저 입력을 기반으로 GPT 호출 후 견적을 생성합니다.",
            security = { @SecurityRequirement(name = "bearerAuth")})
    @PostMapping
    public ResponseEntity<AiEstimateResponse> createEstimate(
            @RequestBody AiEstimateRequest request,
            Authentication authentication
    ) throws IOException {
        String userId = authentication.getName();

        // GPT 호출
        GPTResponse gptResponse = chatService.processUserPrompt(userId, request.getPrompt());

        // 채팅방 조회 or 생성
        ChatRoom room = chatRoomRepository.findById(request.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));

        // 어시스턴트 메시지 저장
        ChatMessage assistantMessage = chatService.saveAssistantMessage(room, gptResponse.getMessage());

        // 견적 생성
        AiEstimate aiEstimate = aiEstimateService.createEstimate(room, assistantMessage, gptResponse.getMessage());

        // DTO 변환
        AiEstimateResponse response = aiEstimateService.toResponse(aiEstimate);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "채팅방 내 AI 견적 조회", description = "특정 채팅방에서 생성된 모든 AI 추천 견적을 조회합니다.")
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<List<AiEstimate>> getEstimates(@PathVariable String roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방이 존재하지 않습니다."));
        return ResponseEntity.ok(aiEstimateService.getEstimates(roomId, room));
    }
}
