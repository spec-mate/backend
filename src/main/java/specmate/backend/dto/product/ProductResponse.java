package specmate.backend.dto.product;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse implements Serializable {
    private Long id;
    private String name;
    private String brand;
    private String category;
    private String image;
    private String transparentImage;

    private BigDecimal priceUsd;
    private Long priceKrw;

    private String availability;
    private String productLink;

    private OffsetDateTime updatedAt;

    private JsonNode detail;
    private String description;
}