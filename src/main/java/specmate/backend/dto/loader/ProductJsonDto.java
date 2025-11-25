package specmate.backend.dto.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
public class ProductJsonDto {
    @JsonProperty("pop_rank")
    private Integer popRank;

    private String category;
    private String name;
    private String manufacturer;
    private Long price;
    private String status;
    private String image;
    private List<String> specs;

    @JsonProperty("product_link")
    private String productLink;

    private String description;
}