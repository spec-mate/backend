package specmate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "estimate_products")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstimateProduct {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id; // 매핑 PK

    @ManyToOne
    @JoinColumn(name = "ai_estimate_id", nullable = false)
    private AiEstimate aiEstimate;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String aiName; // AI가 인식한 부품명

    @Column(nullable = false)
    private Boolean matched = false; // 매핑 성공 여부

    private Integer quantity = 1;
    private Integer unitPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}