package specmate.backend.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@Tag(name = "User API", description = "사용자 정보 관리 API")
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "JWT 토큰에서 추출한 userId로 내 정보를 조회합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping("/me")
    public ResponseEntity<User> getMyInfo(@AuthenticationPrincipal String userId) {
        User user = userService.getMyInfo(userId);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "내 정보 수정", description = "내 닉네임 또는 비밀번호를 수정합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @PutMapping("/me")
    public ResponseEntity<User> updateMyInfo(@AuthenticationPrincipal String userId, @RequestBody UserUpdateRequest request) {
        User updatedUser = userService.updateMyInfo(userId, request);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "내 계정 삭제", description = "내 계정을 영구적으로 삭제합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@AuthenticationPrincipal String userId) {
        userService.deleteMyAccount(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모든 사용자 조회 (ADMIN)", description = "관리자 전용: 모든 사용자 목록을 조회합니다.", security = { @SecurityRequirement(name = "bearerAuth") })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.findAllUsers().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }
}
