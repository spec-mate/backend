package specmate.backend.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.aiestimate.EstimateResult;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.chat.GPTPromptRequest;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.dto.chatroom.ChatRoomRequest;
import specmate.backend.dto.chatroom.ChatRoomResponse;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.service.chat.ChatService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅 메시지/채팅방 관리 API")
public class ChatController {

    private final ChatService chatService;
    private final EstimateResultProcessor estimateResultProcessor;

    /** 프롬프트 전송 */
    @Operation(summary = "유저 프롬프트 전송", description = "유저의 입력을 GPT에 전달합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") })
    @PostMapping("/send-prompt")
    public ResponseEntity<EstimateResult> sendPrompt(
            @RequestBody GPTPromptRequest request,
            Authentication authentication
    ) throws IOException {
        String userId = authentication.getName();
        GPTResponse gptResponse = chatService.processUserPrompt(userId, request.getPrompt());
        EstimateResult result = estimateResultProcessor.parse(gptResponse.getMessage());
        return ResponseEntity.ok(result);
    }

    /** 채팅방 목록 조회 */
    @Operation(summary = "채팅방 목록 조회", description = "유저가 참여한 채팅방 목록을 조회합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> getUserChatRooms(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(chatService.getUserChatRooms(userId));
    }

    /** 채팅방 단일 조회 */
    @Operation(summary = "채팅방 단일 조회", description = "특정 채팅방 정보를 조회합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(chatService.getChatRoom(roomId));
    }

    /**
     * 채팅방 수정 (예: 제목 변경)
     */
    @Operation(summary = "채팅방 수정", description = "채팅방 제목을 수정합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") })
    @PatchMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> updateChatRoom(
            @PathVariable String roomId,
            @RequestBody ChatRoomRequest request
    ) {
        return ResponseEntity.ok(chatService.updateChatRoom(roomId, request));
    }

    /** 채팅방 삭제 */
    @Operation(summary = "채팅방 삭제", description = "채팅방을 삭제합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") })
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteChatRoom(@PathVariable String roomId) {
        chatService.deleteChatRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
