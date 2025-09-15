package specmate.backend.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import specmate.backend.dto.user.SignupRequest;
import specmate.backend.dto.user.SignupResponse;
import specmate.backend.entity.User;
import specmate.backend.repository.UserRepository;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SignupResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("이미 가입된 이메일입니다.");
        }

        String encodedPw = passwordEncoder.encode(req.getPassword());

        User user = User.builder()
                .email(req.getEmail())
                .nickname(req.getNickname())
                .password(encodedPw)
                .build();

        User saved = userRepository.save(user);

        redisTemplate.opsForValue().set(
                "user:" + saved.getId(),
                saved.getEmail(),
                Duration.ofMinutes(10)
        );

        return new SignupResponse(saved.getId(), saved.getEmail(), saved.getNickname());
    }
}
