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

    @Column(nullable = false, length = 1024)
    private String name;

    @Column(nullable = false, length = 128)
    private String brand;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String image;

    @Column(name = "transparent_image", columnDefinition = "TEXT")
    private String transparentImage;

    @Column(name = "price_usd", precision = 12, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "price_krw")
    private Long priceKrw;

    @Column(length = 48)
    private String availability;

    @Column(name = "product_link", columnDefinition = "TEXT")
    private String productLink;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(columnDefinition = "jsonb")
    @Type(JsonType.class)
    private JsonNode detail;

    @Column(columnDefinition = "TEXT")
    private String description;
}