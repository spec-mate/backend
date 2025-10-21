
package specmate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

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
public class UserEstimate {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id; // 사용자 견적 PK

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    private String description;
    private Integer totalPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "userEstimate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserEstimateProduct> products = new ArrayList<>();
}
