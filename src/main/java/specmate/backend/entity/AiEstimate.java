package specmate.backend.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_estimates")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiEstimate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    private ChatRoom chatRoom;

    private String intent;
    private String intro;
    private String note;
    private Long totalPrice;

    @OneToMany(mappedBy = "aiEstimate", cascade = CascadeType.ALL)
    private List<AiEstimateProduct> products = new ArrayList<>();

    private LocalDateTime createdAt;
}