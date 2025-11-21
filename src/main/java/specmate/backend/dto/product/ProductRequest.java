package specmate.backend.dto.product;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {
    private String name;
    private String brand;
    private String category;
    private String image;
    private String transparentImage;

    private BigDecimal priceUsd;
    private Long priceKrw;

    private String availability;
    private String productLink;

    private JsonNode detail;
    private String description;
}