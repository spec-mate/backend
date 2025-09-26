package specmate.backend.dto.product;

import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse implements Serializable {
    private Integer id;
    private String name;
    private String image;
    private Integer popRank;
    private String regDate;

    private Map<String, Object> options;
    private List<Map<String, Object>> priceInfo;
    private Map<String, Object> lowestPrice;

    private String type;
    private String manufacturer;
}
