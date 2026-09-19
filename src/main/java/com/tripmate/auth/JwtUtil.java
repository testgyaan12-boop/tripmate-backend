package com.tripmate.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtUtil(
            @Value("${tripmate.jwt.secret}") String secret,
            @Value("${tripmate.jwt.access-ttl-minutes:15}") long accessTtlMin,
            @Value("${tripmate.jwt.refresh-ttl-days:7}") long refreshTtlDays) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be >= 32 chars (set JWT_SECRET env)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMs = accessTtlMin * 60_000L;
        this.refreshTtlMs = refreshTtlDays * 86_400_000L;
    }

    public String generateAccess(Long userId) {
        return build(userId, "access", accessTtlMs);
    }

    public String generateRefresh(Long userId) {
        return build(userId, "refresh", refreshTtlMs);
    }

    private String build(Long userId, String type, long ttl) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
