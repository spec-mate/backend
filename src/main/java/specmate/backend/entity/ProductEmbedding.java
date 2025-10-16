package specmate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_embeddings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "vector", columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String vector;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "normalized_type", length = 50)
    private String normalizedType;

    @Column(name = "price_numeric")
    private Long priceNumeric;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
