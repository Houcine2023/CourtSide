package com.courtside.api.services;

import static com.courtside.api.TestFixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.courtside.api.entities.Role;
import com.courtside.api.entities.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT integrity, without Spring.
 *
 * The point of these tests is not "does jjwt work" — it is "does OUR configuration
 * make forged or stale tokens impossible to use". Each test is an attack.
 */
@DisplayName("JwtService — token integrity")
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-definitely-long-enough-1234567890";
    private static final String OTHER_SECRET = "a-completely-different-secret-key-0987654321-abcdefgh";

    private JwtService jwtService;
    private User alice;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        alice = member(1L);
        alice.setEmail("alice@test.tn");
    }

    @Test
    @DisplayName("a generated token round-trips back to the user's email")
    void generateThenExtract_returnsSubject() {
        String token = jwtService.generateToken(alice);

        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@test.tn");
    }

    @Test
    @DisplayName("the payload is readable by anyone — so it must never hold secrets")
    void generatedToken_payloadIsNotEncrypted() {
        alice.setRole(Role.MANAGER);
        String token = jwtService.generateToken(alice);

        String payload = new String(
                java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8);

        // Anyone can decode this — which is exactly why the password hash is not in it.
        assertThat(payload).contains("alice@test.tn").contains("MANAGER");
        assertThat(payload).doesNotContain(alice.getPasswordHash());
    }

    @Test
    @DisplayName("a token whose payload was edited is rejected")
    void extractEmail_whenPayloadTampered_throws() {
        String token = jwtService.generateToken(alice);
        String[] parts = token.split("\\.");

        // Forge an ADMIN payload and re-attach the original signature.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker@evil.tn\",\"role\":\"ADMIN\"}"
                        .getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.extractEmail(forged))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("a token signed with another key is rejected")
    void extractEmail_whenSignedWithAnotherKey_throws() {
        SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder()
                .subject("attacker@evil.tn")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        // Structurally perfect, but not signed by us: knowing the format is not enough.
        assertThatThrownBy(() -> jwtService.extractEmail(foreignToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("an expired token is rejected even though the signature is valid")
    void extractEmail_whenExpired_throws() {
        SecretKey ourKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("alice@test.tn")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000)) // a minute ago
                .signWith(ourKey)
                .compact();

        // This is what makes a 15-minute access token actually 15 minutes.
        assertThatThrownBy(() -> jwtService.extractEmail(expired))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("garbage is rejected without crashing the caller")
    void extractEmail_whenNotAToken_throws() {
        assertThatThrownBy(() -> jwtService.extractEmail("not.a.jwt"))
                .isInstanceOf(Exception.class);
    }
}
