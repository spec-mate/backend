package specmate.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AiResponse {
    private String reply;

    private String intent;
    private String intro;
    private String note;

    @JsonProperty("another_input_text")
    private String anotherInputText;

    private String total;

    private Map<String, AiComponent> main;
}
