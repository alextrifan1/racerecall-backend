package com.racerecall.backend.services;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, ONE_HOUR_MS);
    }

    @Test
    @DisplayName("a secret shorter than 32 bytes is rejected when the service is created")
    void constructor_shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService("too-short", ONE_HOUR_MS))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    @DisplayName("generateToke puts the username int the token subject")
    void generateToken_containsUsernameAsSubject() {
        String token = jwtService.generateToken("user");

        assertThat(jwtService.extractUsername(token)).isEqualTo("user");
    }

    @Test
    @DisplayName("isTokenValid retruns true for a fresh token and a matching username")
    void isTokenValid_freshTokenMatchingUsername_returnsTrue() {
        String token = jwtService.generateToken("user");

        assertThat(jwtService.isTokenValid(token, "user")).isTrue();
    }

    @Test
    @DisplayName("isTokenValid returns false when the username does not match the subject")
    void isTokenValid_differentUser_returnsFalse() {
        String token = jwtService.generateToken("alex");

        assertThat(jwtService.isTokenValid(token, "someone-else")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for an expired token")
    void isTokenValid_expiredToken_returnsFalse() {
        JwtService shortLived = new JwtService(SECRET, -1000L);
        String token = shortLived.generateToken("user");

        assertThat(shortLived.isTokenValid(token, "user")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for a token signed with a different secret")
    void isTokenValid_wrongSignature_returnsFalse() {
        JwtService attacker = new JwtService("another-secret-that-is-also-32-characters-long", ONE_HOUR_MS);
        String forgedToken = attacker.generateToken("alex");

        assertThat(jwtService.isTokenValid(forgedToken, "alex")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for a malformed token")
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("this-is-not-a-valid-jwt", "user")).isFalse();
    }

    @Test
    @DisplayName("extractUsername throws on a malformed token (the filter does not catch this yet)")
    void extractUsername_garbageToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtService.extractUsername("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }
}