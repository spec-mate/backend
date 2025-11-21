package specmate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_estimates")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserEstimate {

    @Id
    @UuidGenerator
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "total_price")
    private Long totalPrice;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "userEstimate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserEstimateProduct> products = new ArrayList<>();

    public void calculateTotalPrice() {
        this.totalPrice = products.stream()
            .mapToLong(UserEstimateProduct::getTotalPrice)
            .sum();
    }
}