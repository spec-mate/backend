package specmate.backend.controller.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.user.UserResponse;
import specmate.backend.dto.user.UserUpdateRequest;
import specmate.backend.entity.User;
import specmate.backend.service.user.UserService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    // 내 정보 조회 API
    @GetMapping("/me")
    public ResponseEntity<User> getMyInfo() {
        User user = userService.getMyInfo();
        return ResponseEntity.ok(user);
    }

    // 내 정보 수정 API
    @PutMapping("/me")
    public ResponseEntity<User> updateMyInfo(@RequestBody UserUpdateRequest request) {
        User updatedUser = userService.updateMyInfo(request);
        return ResponseEntity.ok(updatedUser);
    }

    // 내 계정 삭제 API
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount() {
        userService.deleteMyAccount();
        return ResponseEntity.noContent().build();
    }

    // 어드민 전용
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.findAllUsers().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }
}
