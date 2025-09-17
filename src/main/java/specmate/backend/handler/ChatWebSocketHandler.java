package specmate.backend.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.service.chat.ChatService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;

    /** WebSocket 연결 성공 시 실행 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = (String) session.getAttributes().get("token");
        log.info("WebSocket이 연결되었습니다. token={}", token);
    }

    /** 클라이언트 → 서버 메시지 처리 */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String token = (String) session.getAttributes().get("token");
        String userPrompt = message.getPayload();

        // "생각중" 상태 전송
        session.sendMessage(new TextMessage("{\"type\":\"thinking\"}"));

        // GPT 처리 (토큰과 프롬프트 함께 전달)
        GPTResponse gptResponse = chatService.processUserPrompt(token, userPrompt);

        // 결과 전송
        session.sendMessage(new TextMessage(gptResponse.getMessage()));
    }
}
