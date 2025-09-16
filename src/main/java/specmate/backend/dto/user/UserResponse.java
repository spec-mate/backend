package specmate.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import specmate.backend.entity.User;
import specmate.backend.entity.enums.Role;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private String nickname;
    private Role role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
