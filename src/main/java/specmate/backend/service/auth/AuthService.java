package specmate.backend.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import specmate.backend.config.JwtTokenProvider;
import specmate.backend.dto.user.LoginRequest;
import specmate.backend.dto.user.LoginResponse;
import specmate.backend.dto.user.SignupRequest;
import specmate.backend.dto.user.SignupResponse;
import specmate.backend.entity.User;
import specmate.backend.entity.enums.Role;
import specmate.backend.repository.user.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider;
    private final MailService mailService;

    // 회원가입 시, 인증번호 전송
    public void sendVerificationCode(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("이미 가입된 이메일입니다.");
        }

        String code = String.format("%06d", (int)(Math.random() * 1000000));
        redisTemplate.opsForValue().set("verify:" + email, code, Duration.ofMinutes(5));

        mailService.sendEmail(email, "스펙메이트 회원가입 인증번호", "인증번호는 " + code + " 입니다.");
    }

    // 인증번호 확인
    public void verifyEmailCode(String email, String code) {
        String savedCode = (String) redisTemplate.opsForValue().get("verify:" + email);

        if (savedCode == null || !savedCode.equals(code)) {
            throw new RuntimeException("인증번호가 일치하지 않거나 만료되었습니다.");
        }
        redisTemplate.opsForValue().set("verified:" + email, true, Duration.ofMinutes(30));
    }

    // 회원가입
    public SignupResponse signup(SignupRequest req) {
        Boolean verified = (Boolean) redisTemplate.opsForValue().get("verified:" + req.getEmail());
        if (verified == null || !verified) {
            throw new RuntimeException("이메일 인증이 완료되지 않았습니다.");
        }

        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("이미 가입된 이메일입니다.");
        }

        String encodedPw = passwordEncoder.encode(req.getPassword());

        User user = User.builder()
                .email(req.getEmail())
                .nickname(req.getNickname())
                .password(encodedPw)
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);

        redisTemplate.opsForValue().set(
                "user:" + saved.getId(),
                saved.getEmail(),
                Duration.ofMinutes(10)
        );

        return new SignupResponse(saved.getId(), saved.getEmail(), saved.getNickname());
    }

    // 로그인
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 이메일입니다."));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        redisTemplate.opsForValue().set(
                "refresh:" + user.getId(),
                refreshToken,
                Duration.ofDays(7)
        );

        // 닉네임, 이메일도 함께 리턴
        return new LoginResponse(accessToken, refreshToken, user.getNickname(), user.getEmail());
    }

    // 로그아웃
    public void logout(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new RuntimeException("유효하지 않은 Access Token입니다.");
        }

        String userId = jwtTokenProvider.getUserId(accessToken);

        // Redis에서 RefreshToken 제거
        redisTemplate.delete("refresh:" + userId);

        // AccessToken도 블랙리스트 처리(선택 사항, 보안 강화용)
        long expiration = jwtTokenProvider.getExpiration(accessToken);
        redisTemplate.opsForValue().set("blacklist:" + accessToken, "logout", Duration.ofMillis(expiration));
    }

    // 토큰 리프레시
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh Token이 유효하지 않습니다.");
        }

        String userId = jwtTokenProvider.getUserId(refreshToken);
        String saved = (String) redisTemplate.opsForValue().get("refresh:" + userId);

        if (saved == null || !saved.equals(refreshToken)) {
            throw new RuntimeException("Refresh Token이 일치하지 않거나 만료되었습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(),  user.getRole());

        return new LoginResponse(newAccessToken, refreshToken, user.getNickname(), user.getEmail());
    }
}
