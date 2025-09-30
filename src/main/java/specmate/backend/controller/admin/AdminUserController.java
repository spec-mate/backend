package specmate.backend.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.admin.user.UserDto;
import specmate.backend.entity.enums.Role;
import specmate.backend.service.admin.AdminUserService;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - User", description = "어드민 사용자 관리 API")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "사용자 목록 조회", description = "모든 사용자 목록을 페이징 형태로 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<UserDto>> getUsers(Pageable pageable) {
        return ResponseEntity.ok(adminUserService.getUsers(pageable));
    }

    @Operation(summary = "사용자 상세 조회 (ID)", description = "사용자 ID를 통해 상세 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable String id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    @Operation(summary = "사용자 상세 조회 (닉네임)", description = "사용자 닉네임을 통해 상세 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/nickname/{nickname}")
    public ResponseEntity<UserDto> getUserByNickname(@PathVariable String nickname) {
        return ResponseEntity.ok(adminUserService.getUserByNickname(nickname));
    }

    @Operation(summary = "사용자 권한 변경", description = "특정 사용자의 권한(Role)을 변경합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/role")
    public ResponseEntity<Void> updateRole(@PathVariable String id, @RequestBody Role role) {
        adminUserService.updateRole(id, role);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "사용자 삭제", description = "특정 사용자를 탈퇴 처리(물리 삭제)합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping
    public ResponseEntity<Void> deleteUser(@RequestParam String id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
