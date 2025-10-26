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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private ChatMessage message;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "ai_name", nullable = false)
    private String aiName; // AI가 인식한 부품명

    @Column(name = "matched_name")
    private String matchedName;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(nullable = false)
    private Boolean matched = false; // 매핑 성공 여부

    private Integer quantity = 1;
    private Integer unitPrice;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}