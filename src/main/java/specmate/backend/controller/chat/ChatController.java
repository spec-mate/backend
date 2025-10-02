package specmate.backend.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.aiestimate.EstimateResult;
import specmate.backend.dto.chat.ChatMessageRequest;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.chatroom.ChatRoomRequest;
import specmate.backend.dto.chatroom.ChatRoomResponse;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.service.chat.ChatService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Assistant 기반 채팅방/메시지 관리 API")
public class ChatController {

    private final ChatService chatService;
    private final EstimateResultProcessor estimateResultProcessor;

    /** 채팅방 생성 */
    @Operation(
            summary = "채팅방 생성",
            description = "새로운 채팅방을 생성합니다. 내부적으로 Assistant가 자동 생성되어 연결됩니다.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createChatRoom(@RequestBody ChatRoomRequest request) throws IOException {
        ChatRoomResponse room = chatService.createChatRoomWithAssistant(request.getTitle());
        return ResponseEntity.ok(room);
    }

    /** 사용자 메시지 전송 */
    @Operation(
            summary = "메시지 전송",
            description = "특정 채팅방에 메시지를 전송하고, GPT Assistant 응답을 반환합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<EstimateResult> sendMessage(
            @PathVariable String roomId,
            @RequestBody ChatMessageRequest request
    ) throws IOException {
        ChatMessage assistantMsg = chatService.processUserMessage(roomId, request.getPrompt());

        EstimateResult result = estimateResultProcessor.parse(assistantMsg.getContent());
        return ResponseEntity.ok(result);
    }

    /** 채팅방 메시지 조회 */
    @Operation(
            summary = "채팅방 메시지 조회",
            description = "특정 채팅방의 모든 메시지를 조회합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable String roomId) {
        return ResponseEntity.ok(chatService.getChatMessages(roomId));
    }

    /** 채팅방 목록 조회 */
    @Operation(
            summary = "채팅방 목록 조회",
            description = "사용자가 참여한 모든 채팅방을 조회합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> getUserChatRooms(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(chatService.getUserChatRooms(userId));
    }

    /** 채팅방 단일 조회 */
    @Operation(
            summary = "채팅방 단일 조회",
            description = "특정 채팅방 정보를 조회합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(chatService.getChatRoom(roomId));
    }

    /** 채팅방 수정 */
    @Operation(
            summary = "채팅방 수정",
            description = "채팅방 제목을 수정합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PatchMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> updateChatRoom(
            @PathVariable String roomId,
            @RequestBody ChatRoomRequest request
    ) {
        return ResponseEntity.ok(chatService.updateChatRoom(roomId, request));
    }

    /** 채팅방 삭제 */
    @Operation(
            summary = "채팅방 삭제",
            description = "채팅방을 삭제합니다.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteChatRoom(@PathVariable String roomId) {
        chatService.deleteChatRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
