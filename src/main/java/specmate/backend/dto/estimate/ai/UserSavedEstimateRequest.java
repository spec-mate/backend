package specmate.backend.dto.estimate.ai;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSavedEstimateRequest {

    private String aiEstimateId;
    private String title;
    private String description;
    private String total;
    private List<ComponentRequest> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentRequest {
        private String type;
        private String name;
        private String description;

        private Detail detail;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Detail {
            private String price;
            private String image;
        }
    }
}
