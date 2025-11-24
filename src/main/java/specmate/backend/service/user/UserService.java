package specmate.backend.service.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import specmate.backend.dto.user.UserUpdateRequest;
import specmate.backend.entity.User;
import specmate.backend.repository.user.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 모든 유저 정보 조회 (ADMIN 전용)
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    // 내 정보 가져오기
    public User getMyInfo(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
    }

    // 정보 수정
    @Transactional
    public User updateMyInfo(String userId, UserUpdateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));

        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname());
        }

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        user.setUpdatedAt(java.time.LocalDateTime.now());
        return userRepository.save(user);
    }

    // 내 계정 삭제
    @Transactional
    public void deleteMyAccount(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        userRepository.delete(user);
    }
}
