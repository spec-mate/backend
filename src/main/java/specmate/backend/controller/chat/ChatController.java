package specmate.backend.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.chat.GPTPromptRequest;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.service.chat.ChatService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅 메시지/채팅방 관리 API")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "유저 프롬프트 전송", description = "유저의 입력을 GPT에 전달합니다.",
            security = { @SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/send-prompt")
    public ResponseEntity<GPTResponse> sendPrompt(
            @RequestBody GPTPromptRequest request,
            Authentication authentication
    ) throws IOException {
        String userId = authentication.getName();
        GPTResponse gptResponse = chatService.processUserPrompt(userId, request.getPrompt());
        return ResponseEntity.ok(gptResponse);
    }

    @Operation(summary = "채팅방 목록 조회", description = "유저가 참여한 채팅방 목록을 조회합니다.")
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> getUserChatRooms(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(chatService.getUserChatRooms(userId));
    }

    @Operation(summary = "채팅 메시지 조회", description = "채팅방의 메시지 목록을 조회합니다.")
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessage>> getChatMessages(@PathVariable String roomId) {
        return ResponseEntity.ok(chatService.getChatMessages(roomId));
    }

    @Operation(summary = "채팅방 삭제", description = "채팅방을 삭제합니다.")
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteChatRoom(@PathVariable String roomId) {
        chatService.deleteChatRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
