package specmate.backend.dto.product;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductJsonDto {
    private String name;
    private String image;
    private Integer pop_rank;
    private String reg_date;
    private Map<String, Object> options;
    private List<Map<String, Object>> price_info;
    private Map<String, Object> lowest_price;
    private String type;
    private String manufacturer;
}