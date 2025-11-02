package specmate.backend.dto.estimate.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import specmate.backend.entity.AiEstimate;
import specmate.backend.entity.EstimateProduct;
import specmate.backend.entity.Product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSavedEstimateResponse {

    private String id;
    private String title;
    private String description;

    @JsonProperty("total")
    private Integer totalPrice;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("components")
    private List<ComponentResponse> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentResponse {

        @JsonAlias({"category", "type"})
        private String type;

        @JsonProperty("name")
        private String name;

        private String description;

        @JsonProperty("detail")
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

    public static UserSavedEstimateResponse fromEntity(AiEstimate entity, List<EstimateProduct> products) {
        return UserSavedEstimateResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .totalPrice(entity.getTotalPrice())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .components(products.stream()
                        .map(p -> {
                            Product product = p.getProduct();

                            String type = (product != null)
                                    ? product.getType()
                                    : "unknown";
                            String imageUrl = (product != null && product.getImage() != null)
                                    ? product.getImage()
                                    : "";

                            return ComponentResponse.builder()
                                    .type(type)
                                    .name(p.getMatchedName() != null ? p.getMatchedName() : p.getAiName())
                                    .description(product != null ? product.getManufacturer() : "기본형 구성 부품")
                                    .detail(ComponentResponse.Detail.builder()
                                            .price(String.valueOf(p.getUnitPrice()))
                                            .image(imageUrl)
                                            .build())
                                    .build();
                        })
                        .collect(Collectors.toList()))
                .build();
    }
}
