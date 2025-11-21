package specmate.backend.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    /** WebSocket 핸드셰이크 직전 실행 */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String token = httpRequest.getParameter("token");

            System.out.println("WebSocket Handshake token: " + token);

            if (token == null || token.isBlank()) {
                System.out.println("토큰 없음 → handshake 실패");
                return false;
            }

            // JWT 유효성 검증
            if (!jwtTokenProvider.validateToken(token)) {
                System.out.println("토큰 유효성 검사 실패 → handshake 실패");
                return false;
            }

            try {
            // 유저 정보 추출 (userId, role, email 등 필요시 확장)
            String userId = jwtTokenProvider.getUserId(token);
            String role = jwtTokenProvider.getRole(token);
            String email = jwtTokenProvider.getEmail(token);

            // WebSocket 세션에 저장
            attributes.put("token", token);
            attributes.put("userId", userId);
            attributes.put("role", role);
            attributes.put("email", email);

            System.out.println("WebSocket Handshake 성공 → userId=" + userId + ", role=" + role + ", email=" + email);
            return true;

            } catch (Exception e) {
            System.out.println("WebSocket Handshake 중 예외 발생: " + e.getMessage());
            return false;
            }
        }
        return false;
    }

    /** WebSocket 핸드셰이크 후 실행 */
    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
