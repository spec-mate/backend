package specmate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_estimates")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiEstimate {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id; // AI 견적 PK

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "assistant_id", nullable = false)
    private Assistant assistant;

    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message; // 요구사항 메시지

    @Column(nullable = false)
    private String title;

    private Integer totalPrice;

    @Column(nullable = false)
    private String status = "active"; // active, updated, deleted

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}