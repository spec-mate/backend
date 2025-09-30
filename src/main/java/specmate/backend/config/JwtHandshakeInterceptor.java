package specmate.backend.config;

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

    /** WebSocket 핸드셰이크(연결 수립) 직전에 실행되는 메서드 */
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String token = httpRequest.getParameter("token");

            // 토큰 검증
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String userId = jwtTokenProvider.getUserId(token);
                String role = jwtTokenProvider.getRole(token);

                // WebSocket 세션에 필요한 정보 저장
                attributes.put("token", token);
                attributes.put("userId", userId);
                attributes.put("role", role);

                System.out.println("WebSocket 토큰 검증 성공: userId=" + userId + ", role=" + role);
                return true;
            } else {
                System.out.println("WebSocket 토큰 검증 실패");
                return false;
            }
        }
        return false;
    }

    /** WebSocket 핸드셰이크가 끝난 후 실행되는 메서드 */
    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
