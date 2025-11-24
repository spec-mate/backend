package specmate.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain filterChain) throws ServletException, IOException {

        String path = req.getRequestURI();
        String method = req.getMethod();

        if (path.startsWith("/api/auth/") ||
                path.startsWith("/swagger-ui/") ||
                path.startsWith("/v3/api-docs/") ||
                path.startsWith("/health") ||
                path.equals("/") ||
                path.startsWith("/ws/") ||
                (path.startsWith("/api/product/") && "GET".equals(method))) {
            filterChain.doFilter(req, res);
            return;
        }

        String header = req.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            // 블랙리스트 체크
            if (redisTemplate.hasKey("blacklist:" + token)) {
                log.warn("Blacklisted token attempted for path: {}", path);
                filterChain.doFilter(req, res);
                return;
            }

            if (jwtTokenProvider.validateToken(token)) {
                String userId = jwtTokenProvider.getUserId(token);
                String role = jwtTokenProvider.getRole(token);

                log.debug("JWT Authentication - userId: {}, role: {}, path: {}", userId, role, path);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                log.warn("Invalid JWT token for path: {}", path);
            }
        } else {
            log.debug("No Authorization header for path: {}", path);
        }

        filterChain.doFilter(req, res);
    }
}
