package xiaozhu.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 用于生成和解析用户登录Token
 * 
 * @author XiaoZhuDaBai
 * @version 3.0
 * @date 2025/10/10 13:28
 */
public class JwtUtil {

    /**
     * JWT密钥
     */
    private final String secret;

    /**
     * Token过期时间（单位：毫秒）
     */
    private final long expireTime;

    /**
     * 构造函数
     * 
     * @param secret JWT密钥
     * @param expireTime Token过期时间（单位：毫秒）
     */
    public JwtUtil(String secret, long expireTime) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT secret cannot be null or empty");
        }
        if (expireTime <= 0) {
            throw new IllegalArgumentException("JWT expire time must be greater than 0");
        }
        this.secret = secret;
        this.expireTime = expireTime;
    }

    /**
     * 获取密钥
     * 使用配置文件中的密钥字符串生成SecretKey对象
     * 
     * @return SecretKey对象
     */
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成Token
     * 
     * @param userId 用户ID
     * @param openid 微信openid
     * @return JWT Token字符串
     */
    public String generateToken(Long userId, String openid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("openid", openid);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireTime))
                .subject(String.valueOf(userId)) // 设置主题为用户ID，便于在网关中获取
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析Token
     * 
     * @param token JWT Token字符串
     * @return Token中的声明信息
     * @throws io.jsonwebtoken.JwtException 如果Token无效或过期
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证Token是否有效
     * 
     * @param token JWT Token字符串
     * @return 如果Token有效返回true，否则返回false
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从Token中获取用户ID
     * 
     * @param token JWT Token字符串
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        } else if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        } else if (userId instanceof String) {
            return Long.parseLong((String) userId);
        }
        // 如果claims中没有userId，尝试从subject获取
        String subject = claims.getSubject();
        return subject != null ? Long.parseLong(subject) : null;
    }

    /**
     * 从Token中获取openid
     * 
     * @param token JWT Token字符串
     * @return 微信openid
     */
    public String getOpenidFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("openid", String.class);
    }

    /**
     * 检查Token是否过期
     * 
     * @param token JWT Token字符串
     * @return 如果Token已过期返回true，否则返回false
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}