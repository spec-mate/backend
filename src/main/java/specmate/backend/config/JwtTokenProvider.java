package specmate.backend.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import specmate.backend.entity.enums.Role;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

//    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final Key key;
    private final long accessTokenValidity = 1000L * 60 * 30;
    private final long refreshTokenValidity = 1000L * 60 * 60 * 24 * 7;

    public JwtTokenProvider(@Value("${JWT_SECRET}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    // Access Token 생성
    public String createAccessToken(String userId, String email, Role role) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("email", email)
                .claim("role", "ROLE_" + role.name())   // 권한 추가
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenValidity))
                .signWith(key)
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenValidity))
                .signWith(key)
                .compact();
    }

//    // 토큰내 유저 ID 추출
//    public String getUserId(String token) {
//        return Jwts.parserBuilder().setSigningKey(key).build()
//                .parseClaimsJws(token).getBody().getSubject();
//    }
//
//    public String getRole(String token) {
//        return Jwts.parserBuilder()
//                .setSigningKey(key)
//                .build()
//                .parseClaimsJws(token)
//                .getBody()
//                .get("role", String.class);
//    }
//
//    // 토큰 내 Email 추출
//    public String getEmail(String token) {
//        return Jwts.parserBuilder()
//                .setSigningKey(key)
//                .build()
//                .parseClaimsJws(token)
//                .getBody()
//                .get("email", String.class);
//    }

    /** Claims 공통 추출 */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 토큰내 유저 ID 추출
    public String getUserId(String token) {
        return parseClaims(token).getSubject();
    }
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // 토큰 내 Email 추출
    public String getEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    // 토큰 유효성 검사
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 만료시간 구하기
    public long getExpiration(String token) {
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }
}
