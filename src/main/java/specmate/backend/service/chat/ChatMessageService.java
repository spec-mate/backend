package specmate.backend.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import specmate.backend.entity.ChatMessage;
import specmate.backend.entity.ChatRoom;
import specmate.backend.entity.enums.MessageStatus;
import specmate.backend.entity.enums.SenderType;
import specmate.backend.repository.chat.ChatMessageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;


    /** 사용자 메시지 저장 */
    public ChatMessage saveUserMessage(ChatRoom room, String content) {
        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.USER)
                .content(content)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return chatMessageRepository.save(msg);
    }

    /** GPT 응답 메시지 저장 */
    public ChatMessage saveAssistantMessage(ChatRoom room, String reply) {
        ChatMessage msg = ChatMessage.builder()
                .chatRoom(room)
                .sender(SenderType.ASSISTANT)
                .content(reply)
                .status(MessageStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            // GPT 응답 중 JSON만 추출
            String jsonPart = extractJsonOnly(reply);

            // JSON이 유효하면 parsedJson에 저장
            if (jsonPart != null && jsonPart.contains("{") && jsonPart.contains("}")) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> parsed = mapper.readValue(jsonPart, Map.class);
                msg.setParsedJson(parsed);
            }
        } catch (Exception e) {
            log.debug("JSON 파싱 실패, 자연어 응답으로 처리합니다: {}", e.getMessage());
        }

        return chatMessageRepository.save(msg);
    }

    /** 특정 채팅방의 메시지 목록 */
    public List<ChatMessage> getMessages(ChatRoom room) {
        return chatMessageRepository.findByChatRoomOrderByCreatedAtAsc(room);
    }

    /** GPT 응답에서 JSON 부분만 추출 */
    private String extractJsonOnly(String s) {
        if (s == null) return null;
        String t = s.trim();
        Pattern code = Pattern.compile("```json\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
        Matcher m1 = code.matcher(t);
        if (m1.find()) return m1.group(1);
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) return t.substring(first, last + 1);
        return null;
    }
}
