package specmate.backend.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import specmate.backend.dto.chat.GPTResponse;
import specmate.backend.dto.aiestimate.EstimateResult;
import specmate.backend.processor.EstimateResultProcessor;
import specmate.backend.service.chat.ChatService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final EstimateResultProcessor estimateResultProcessor;

    /** WebSocket 연결 성공 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = (String) session.getAttributes().get("token");
        log.info("WebSocket 연결됨. token={}", token);
    }

    /** 메시지 처리 */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String token = (String) session.getAttributes().get("token");
        String userPrompt = message.getPayload();

        // 클라이언트에 "생각중" 상태 전달
        session.sendMessage(new TextMessage("{\"type\":\"thinking\"}"));

        // GPT 호출
        GPTResponse gptResponse = chatService.processUserPromptWithToken(token, userPrompt);

        // GPT 응답을 전처리된 DTO로 변환
        EstimateResult estimateResult = estimateResultProcessor.parse(gptResponse.getMessage());

        // 최종 응답 전송 (camelCase JSON으로 직렬화됨)
        session.sendMessage(new TextMessage(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(estimateResult)
        ));
    }
}
