package com.gridveritas.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

/**
 * Mints and validates HS256 JWTs. The signing key is derived as SHA-256 of the
 * configured secret, so any secret length yields a valid 256-bit key.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${gridveritas.security.jwt.secret}") String secret,
                      @Value("${gridveritas.security.jwt.ttl-minutes:120}") long ttlMinutes) {
        this.key = deriveKey(secret);
        this.ttlMillis = ttlMinutes * 60_000L;
    }

    private static SecretKey deriveKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot derive JWT key", e);
        }
    }

    /** role is a bare role name, e.g. "ADMIN" or "INGEST". */
    public String issue(String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttlMillis / 1000;
    }

    /** Returns the parsed claims, or throws if the token is invalid/expired. */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }
}
