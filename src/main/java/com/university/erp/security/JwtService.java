package com.university.erp.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private final SecretKey key;
    private final long      expirationMs;
    private final long      refreshExpirationMs;

    public JwtService(
        @Value("${app.jwt.secret}")                  String secret,
        @Value("${app.jwt.expiration-ms}")           long   expirationMs,
        @Value("${app.jwt.refresh-expiration-ms}")   long   refreshExpirationMs
    ) {
        this.key                 = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs        = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UserDetails user) {
        return buildToken(user, expirationMs, Map.of("type", "access"));
    }

    public String generateRefreshToken(UserDetails user) {
        return buildToken(user, refreshExpirationMs, Map.of("type", "refresh"));
    }

    public long getExpirationMs() { return expirationMs; }

    public boolean isValid(String token, UserDetails user) {
        try {
            String username = extractUsername(token);
            Integer version = extractClaim(token, claims -> claims.get("version", Integer.class));
            int current = user instanceof com.university.erp.entity.User account ? account.getTokenVersion() : 0;
            return username.equals(user.getUsername()) && user.isEnabled() && !isExpired(token)
                && (version == null ? 0 : version) == current;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean hasType(String token, String type) {
        return type.equals(extractClaim(token, claims -> claims.get("type", String.class)));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return resolver.apply(claims);
    }

    private String buildToken(UserDetails user, long ttlMs, Map<String, Object> extra) {
        Date now = new Date();
        return Jwts.builder()
            .claims(extra)
            .claim("version", user instanceof com.university.erp.entity.User account ? account.getTokenVersion() : 0)
            .subject(user.getUsername())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + ttlMs))
            .signWith(key)
            .compact();
    }
}
