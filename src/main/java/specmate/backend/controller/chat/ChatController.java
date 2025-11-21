package specmate.backend.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.ai.AiResponse;
import specmate.backend.dto.chat.ChatRequest;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.repository.chat.ChatMessageRepository;
import specmate.backend.service.chat.ChatService;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Chat API", description = "AI 채팅 및 견적 상담 API")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;

    // 채팅방 생성 (상담 시작)
    @Operation(summary = "채팅방 생성", description = "새로운 견적 상담 채팅방을 생성합니다.")
    @PostMapping("/room")
    public ResponseEntity<ChatRoom> createChatRoom(@AuthenticationPrincipal String userId) {
        ChatRoom chatRoom = chatService.createChatRoom(userId);
        return ResponseEntity.ok(chatRoom);
    }

    // 내 채팅방 목록 조회
    @Operation(summary = "내 채팅방 목록", description = "사용자의 과거 상담 이력을 조회합니다.")
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> getUserChatRooms(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(chatService.getUserChatRooms(userId));
    }

    // 메시지 전송 (유저 -> AI)
    @Operation(summary = "메시지 전송", description = "AI에게 메시지를 보냅니다. (답변 생성 시간 소요됨)")
    @PostMapping("/room/{roomId}/message")
    public ResponseEntity<AiResponse> sendMessage(
        @PathVariable Long roomId,
        @RequestBody ChatRequest request,
        @AuthenticationPrincipal String userId) {

        AiResponse response = chatService.processUserMessage(roomId, userId, request.getContent());

        return ResponseEntity.ok(response);
    }

    // 채팅방 메시지 내역 조회
    @Operation(summary = "대화 내역 조회", description = "특정 채팅방의 이전 대화 내용을 모두 불러옵니다.")
    @GetMapping("/room/{roomId}/messages")
    public ResponseEntity<List<ChatMessage>> getChatHistory(
        @PathVariable Long roomId,
        @AuthenticationPrincipal String userId) {
        List<ChatMessage> history = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        return ResponseEntity.ok(history);
    }
}