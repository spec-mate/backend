package specmate.backend.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String image;

    private Integer popRank;

    private String regDate;

    /** 옵션 정보 (Map 구조 → jsonb 저장) */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> options;

    /** 가격 정보 (리스트 구조 → jsonb 저장) */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> priceInfo;

    /** 최저가 정보 */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> lowestPrice;

    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "vector(1536)")
    private float[] vector;

    @Column(nullable = false)
    private String manufacturer;
}