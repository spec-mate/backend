package specmate.backend.dto.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
public class ProductJsonDto {
    private String name;
    private String brand;
    private String category;
    private String image;

    @JsonProperty("transparent_image")
    private String transparentImage;

    @JsonProperty("price_usd")
    private BigDecimal priceUsd;

    @JsonProperty("price_krw")
    private Long priceKrw;

    private String availability;

    @JsonProperty("product_link")
    private String productLink;

    @JsonProperty("updated_at")
    private String updatedAt;

    private JsonNode detail;
    private String description;
}