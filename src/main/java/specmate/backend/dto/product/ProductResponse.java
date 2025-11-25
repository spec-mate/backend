package specmate.backend.dto.product;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse implements Serializable {
    private Long id;
    private Integer popRank;
    private String category;
    private String name;
    private String manufacturer;
    private Long price;
    private String status;
    private String image;
    private JsonNode specs;
    private String productLink;
    private String description;
    private OffsetDateTime updatedAt;
}