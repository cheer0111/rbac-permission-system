package cheer.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类
 * <p>
 * 基于 jjwt 库，使用 HMAC-SHA256 签名算法
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 生成 JWT token
     *
     * @param userId      用户ID（存入 subject）
     * @param username    用户名（自定义 claim）
     * @param permissions 权限标识列表（自定义 claim）
     * @return JWT token 字符串
     */
    public String generateToken(Long userId, String username, List<String> permissions) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 JWT token，返回 Claims（包含 subject、自定义 claim）
     * <p>
     * token 无效或过期时会抛出异常，由调用方处理
     *
     * @param token JWT token 字符串
     * @return Claims
     */
    public Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
