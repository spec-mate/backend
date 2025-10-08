package specmate.backend.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import specmate.backend.dto.user.*;
import specmate.backend.service.auth.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입 / 로그인 / 이메일 인증 API")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "인증번호 전송", description = "사용자 이메일로 인증번호 전송.")
    @PostMapping("/send-code")
    public ResponseEntity<String> sendCode(@RequestBody EmailRequest request) {
        authService.sendVerificationCode(request.getEmail());
        return ResponseEntity.ok("인증번호가 이메일로 발송되었습니다.");
    }

    @Operation(summary = "인증번호 확인", description = "사용자가 입력한 인증번호 검증")
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyCode(@RequestBody VerifyCodeRequest request) {
        authService.verifyEmailCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
    }

    @Operation(summary = "회원가입", description = "이메일 인증이 완료된 사용자만 회원가입 가능")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> register(@Valid @RequestBody SignupRequest req) {
        SignupResponse response = authService.signup(req);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인시 JWT 토큰 발급")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse response = authService.login(req);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "로그아웃", description = "AccessToken을 블랙리스트 처리, RefreshToken을 제거")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "토큰 재발급", description = "RefreshToken을 사용해 새로운 AccessToken을 발급합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestParam String refreshToken) {
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }
}