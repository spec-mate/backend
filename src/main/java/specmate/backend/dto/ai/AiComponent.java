package specmate.backend.dto.ai;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AiComponent {
    private String name;
    private String category;
    private Long price;
    private String description;
}
