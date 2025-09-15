package specmate.backend.controller.health;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthCheckController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    public HealthCheckController(DataSource dataSource, StringRedisTemplate redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> status = new HashMap<>();

        // 기본 서버 상태
        status.put("server", "UP");

        // DB 체크
        try (Connection conn = dataSource.getConnection()) {
            status.put("database", conn.isValid(1) ? "UP" : "DOWN");
        } catch (Exception e) {
            status.put("database", "DOWN");
        }

        // Redis 체크
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            status.put("redis", "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN");
        } catch (Exception e) {
            status.put("redis", "DOWN");
        }

        return ResponseEntity.ok(status);
    }
}
