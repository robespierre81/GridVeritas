package com.gridveritas.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token mint/validate round-trip and the failure modes that matter for auth:
 * a token must not validate against a different secret, and an expired token
 * must not validate at all. No Spring context - JwtService only needs its
 * two constructor values.
 */
class JwtServiceTest {

    @Test
    void issuedTokenParsesBackToTheSameSubjectAndRole() {
        JwtService service = new JwtService("test-secret-value-not-used-in-prod", 120);

        String token = service.issue("alice", "ADMIN");
        Claims claims = service.parse(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void tokenSignedWithADifferentSecretDoesNotValidate() {
        JwtService issuer = new JwtService("secret-one", 120);
        JwtService verifier = new JwtService("secret-two", 120);

        String token = issuer.issue("alice", "ADMIN");

        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenDoesNotValidate() {
        JwtService service = new JwtService("test-secret-value-not-used-in-prod", 120);
        String token = service.issue("alice", "ADMIN");

        // Flip a character in the payload segment.
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[payload.length / 2] = payload[payload.length / 2] == 'a' ? 'b' : 'a';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenDoesNotValidate() throws InterruptedException {
        JwtService service = new JwtService("test-secret-value-not-used-in-prod", 0);
        String token = service.issue("alice", "ADMIN");

        Thread.sleep(50); // guarantee "now" has moved past a 0-minute TTL

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void ttlSecondsMatchesConfiguredMinutes() {
        JwtService service = new JwtService("test-secret-value-not-used-in-prod", 5);

        assertThat(service.ttlSeconds()).isEqualTo(300);
    }
}
