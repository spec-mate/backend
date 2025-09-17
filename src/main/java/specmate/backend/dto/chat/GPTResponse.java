package specmate.backend.dto.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GPTResponse {

    private List<Choice> choices;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;
        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public String getMessage() {
        if(choices != null && !choices.isEmpty() && choices.get(0).getMessage() != null) {
            return choices.get(0).getMessage().getContent();
        }
        return "";
    }
}