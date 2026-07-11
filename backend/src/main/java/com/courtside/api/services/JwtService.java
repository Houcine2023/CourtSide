package com.courtside.api.services;

import org.springframework.stereotype.Service;

import com.courtside.api.entities.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;



@Service
public class JwtService {
    
    @Value("${app.jwt.secret}")
    private String secret;

    private final long jwtExpiration = 15 * 60 * 1000;

    private SecretKey getSigningKey() {
        // Always pin the charset: getBytes() without one depends on the OS default.
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(
                    new Date(
                        System.currentTimeMillis()+ jwtExpiration
                    )
                )
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
