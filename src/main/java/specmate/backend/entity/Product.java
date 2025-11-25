package specmate.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;
import org.hibernate.annotations.Type;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pop_rank")
    private Integer popRank;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 1024)
    private String name;

    @Column(nullable = false, length = 128)
    private String manufacturer;

    @Column(nullable = false)
    private Long price;

    @Column(length = 32)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String image;

    @Column(columnDefinition = "jsonb")
    @Type(JsonType.class)
    private JsonNode specs;

    @Column(name = "product_link", columnDefinition = "TEXT")
    private String productLink;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}