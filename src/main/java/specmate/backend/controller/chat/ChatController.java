package specmate.backend.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.estimate.ai.EstimateResponse;
import specmate.backend.dto.estimate.ai.EstimateResult;
import specmate.backend.dto.chat.ChatMessageRequest;
import specmate.backend.dto.chat.ChatMessageResponse;
import specmate.backend.dto.chatroom.ChatRoomRequest;
import specmate.backend.dto.chatroom.ChatRoomResponse;
import specmate.backend.entity.ChatMessage;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.repository.chat.AiEstimateRepository;
import specmate.backend.service.chat.ChatService;
import specmate.backend.service.chat.ChatRoomService;
import specmate.backend.service.chat.ChatMessageService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅방/메시지 관리 API")
public class ChatController {

    private final ChatService chatService;             // GPT 견적 생성 처리
    private final ChatRoomService chatRoomService;     // 채팅방 CRUD
    private final ChatMessageService chatMessageService; // 메시지 조회
    private final EstimateResultProcessor estimateResultProcessor;
    private final AiEstimateRepository aiEstimateRepository;

    /** 채팅방 생성 */
    @Operation(summary = "채팅방 생성", description = "새로운 채팅방을 생성합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            @RequestBody ChatRoomRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        ChatRoomResponse room = chatRoomService.createChatRoom(userId, request.getTitle());
        return ResponseEntity.ok(room);
    }

    /** 사용자 메시지 전송 (RAG + GPT 견적 생성) */
    @Operation(summary = "메시지 전송", description = "사용자의 메시지를 받아 GPT 견적 응답을 생성합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<EstimateResponse> sendMessage(
            @PathVariable String roomId,
            @RequestBody ChatMessageRequest request
    ) throws IOException {
        // ChatService에서 EstimateResponse를 바로 받음
        EstimateResponse response = chatService.handleUserMessage(roomId, request.getPrompt());

        // 프론트에 EstimateResponse 반환
        return ResponseEntity.ok(response);
    }

    /** 특정 채팅방의 메시지 목록 조회 */
    @Operation(summary = "채팅방 메시지 조회", description = "특정 채팅방의 모든 메시지를 조회합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable String roomId) {
        List<ChatMessage> messages = chatMessageService.getMessages(
                chatRoomService.getEntityById(roomId)
        );
        List<ChatMessageResponse> response = messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    /** 사용자별 채팅방 목록 조회 */
    @Operation(summary = "채팅방 목록 조회", description = "사용자가 생성한 모든 채팅방을 조회합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> getUserChatRooms(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(chatRoomService.getUserChatRooms(userId));
    }

    /** 단일 채팅방 조회 */
    @Operation(summary = "채팅방 단일 조회", description = "특정 채팅방의 정보를 조회합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(chatRoomService.getChatRoom(roomId));
    }

    /** 채팅방 제목 수정 */
    @Operation(summary = "채팅방 수정", description = "채팅방 제목을 수정합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @PatchMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> updateChatRoom(
            @PathVariable String roomId,
            @RequestBody ChatRoomRequest request
    ) {
        return ResponseEntity.ok(chatRoomService.updateChatRoom(roomId, request));
    }

    /** 채팅방 삭제 */
    @Operation(summary = "채팅방 삭제", description = "채팅방 및 관련 데이터를 삭제합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteChatRoom(@PathVariable String roomId) {
        chatRoomService.deleteChatRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
