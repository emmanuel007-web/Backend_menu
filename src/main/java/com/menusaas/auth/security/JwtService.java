package com.menusaas.auth.security;

import com.menusaas.config.AppProperties;
import com.menusaas.users.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Emisión y validación de JWT (firma HS256).
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_RESTAURANT_ID = "rid";

    private final SecretKey key;
    private final AppProperties.Jwt jwtProps;

    public JwtService(AppProperties appProperties) {
        this.jwtProps = appProperties.jwt();
        this.key = Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(CLAIM_USER_ID, user.getId());
        if (user.getRestaurant() != null) {
            claims.put(CLAIM_RESTAURANT_ID, user.getRestaurant().getId());
        }
        claims.put("role", user.getRole().getName());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProps.accessTokenTtlMinutes(), ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claims(Map.of(CLAIM_USER_ID, user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProps.refreshTokenTtlDays(), ChronoUnit.DAYS)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida firma y expiración. Lanza JwtException si el token es inválido.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(jwtProps.refreshTokenTtlDays(), ChronoUnit.DAYS);
    }

    public long accessTokenTtlSeconds() {
        return jwtProps.accessTokenTtlMinutes() * 60L;
    }
}