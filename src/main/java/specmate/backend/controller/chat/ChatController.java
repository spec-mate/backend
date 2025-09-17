package specmate.backend.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import specmate.backend.config.JwtTokenProvider;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.service.chat.ChatService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅 및 AI 추천 견적 API")
public class ChatController {

    private final ChatService chatService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "유저 프롬프트 전송", description = "유저의 입력을 GPT에 전달하고, 채팅 및 견적을 저장합니다.")
    @PostMapping("/send-prompt")
    public ResponseEntity<GPTResponse> sendPrompt(
            @RequestHeader("Authorization") String authorization,
            @Parameter(description = "사용자 입력") @RequestParam String prompt
    ) throws IOException{
        String token = authorization.replace("Bearer", "");
        String userId = jwtTokenProvider.getUserId(token);

        GPTResponse gptResponse = chatService.processUserPrompt(userId, prompt);
        return ResponseEntity.ok(gptResponse);

    }

    @Operation(summary = "채팅방 목록 조회", description = "유저가 참여한 채팅방 목록을 조회합니다.")
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> getUserChatRooms(
            @RequestHeader("Authorization") String authorization
    ) {
        String token = authorization.replace("Bearer ", "");
        String userId = jwtTokenProvider.getUserId(token);

        return ResponseEntity.ok(chatService.getUserChatRooms(userId));
    }

    @Operation(summary = "채팅 메세지 조회", description = "특정 채팅방의 메세지 목록을 조회합니다.")
    @GetMapping("/rooms/{roomid}/messages")
    public ResponseEntity<List<ChatMessage>> getChatMessages(
            @PathVariable String roomid
    ){
        return ResponseEntity.ok(chatService.getChatMessages(roomid));
    }

    @Operation(summary = "AI 견적 조회", description = "특정 채팅방에서 생성된 AI 추천 견적을 조회합니다.")
    @GetMapping("/rooms/{roomId}/estimates")
    public ResponseEntity<List<AiEstimate>> getEstimates(
            @PathVariable String roomId
    ) {
        return ResponseEntity.ok(chatService.getEstimates(roomId));
    }

    @Operation(summary = "채팅방 삭제", description = "특정 채팅방과 관련된 메시지/견적을 모두 삭제합니다.")
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteChatRoom(
            @PathVariable String roomId
    ) {
        chatService.deleteChatRoom(roomId);
        return ResponseEntity.noContent().build();
    }

}
