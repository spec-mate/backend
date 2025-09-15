package specmate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_estimate_products")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEstimateProduct {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id; // 매핑 PK

    @ManyToOne
    @JoinColumn(name = "user_estimate_id", nullable = false)
    private UserEstimate userEstimate;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String category; // CPU / GPU / SSD ...

    private Integer quantity = 1;
    private Integer unitPrice;
    private Integer totalPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}