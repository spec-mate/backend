package specmate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.postgresql.util.PGobject;

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

    @Column(columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String vector;

    @Column(columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;
}
