package com.nau.animalhospital.cs.security;

import com.nau.animalhospital.cs.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtTokenUtil {
    private final AppProperties appProperties;

    public JwtTokenUtil(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    private SecretKey getKey() {
        String secret = appProperties.getJwt().getSecret();
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username, String role, Long tenantId) {
        Instant now = Instant.now();
        Instant expireAt = now.plus(appProperties.getJwt().getExpireMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .claim("tid", tenantId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(getKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
    }
}
