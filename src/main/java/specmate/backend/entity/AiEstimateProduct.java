package specmate.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ai_estimate_products")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class AiEstimateProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_estimate_id")
    private AiEstimate aiEstimate;

    private String category;
    private String name;
    private Long price;

    @Column(columnDefinition = "TEXT")
    private String image;

    @Column(length = 1000)
    private String description; // AI가 설명한 내용
}