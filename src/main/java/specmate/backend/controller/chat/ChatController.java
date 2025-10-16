package specmate.backend.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.service.chat.ChatService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅방/메시지 관리 API")
public class ChatController {

    private final ChatService chatService;
    private final EstimateResultProcessor estimateResultProcessor;

    /** 채팅방 생성 */
    @Operation(
            summary = "채팅방 생성",
            description = "새로운 채팅방을 생성합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "생성 성공",
                            content = @Content(schema = @Schema(implementation = ChatRoomResponse.class))),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
            }
    )
    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            @RequestBody ChatRoomRequest request,
            Authentication authentication
    ) throws IOException {
        String userId = authentication.getName();
        ChatRoomResponse room = chatService.createChatRoom(userId, request.getTitle());
        return ResponseEntity.ok(room);
    }

    /** 사용자 메시지 전송 (RAG + GPT 견적 생성) */
    @Operation(
            summary = "메시지 전송",
            description = """
                    사용자의 메시지를 받아 PgVector 기반 문서 검색을 수행하고,
                    OpenAI ChatModel로부터 JSON 형식의 견적 응답을 생성하여 반환합니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "GPT 응답 성공",
                            content = @Content(schema = @Schema(implementation = EstimateResult.class))),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "500", description = "GPT 응답 처리 중 서버 오류")
            }
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
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
                    @ApiResponse(responseCode = "404", description = "채팅방 없음"),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable String roomId) {
        return ResponseEntity.ok(chatService.getChatMessages(roomId));
    }

    /** 채팅방 목록 조회 */
    @Operation(
            summary = "채팅방 목록 조회",
            description = "사용자가 생성한 모든 채팅방을 조회합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = ChatRoomResponse.class))),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> getUserChatRooms(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(chatService.getUserChatRooms(userId));
    }

    /** 채팅방 단일 조회 */
    @Operation(
            summary = "채팅방 단일 조회",
            description = "특정 채팅방의 정보를 조회합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = ChatRoomResponse.class))),
                    @ApiResponse(responseCode = "404", description = "채팅방 없음"),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(chatService.getChatRoom(roomId));
    }

    /** 채팅방 수정 */
    @Operation(
            summary = "채팅방 수정",
            description = "채팅방 제목을 수정합니다.",
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "수정 성공",
                            content = @Content(schema = @Schema(implementation = ChatRoomResponse.class))),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
                    @ApiResponse(responseCode = "404", description = "채팅방 없음")
            }
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
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공 (본문 없음)"),
                    @ApiResponse(responseCode = "404", description = "채팅방 없음"),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteChatRoom(@PathVariable String roomId) {
        chatService.deleteChatRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
