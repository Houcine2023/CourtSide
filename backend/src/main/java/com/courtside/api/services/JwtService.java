package com.courtside.api.services;

import org.springframework.stereotype.Service;

import com.courtside.api.entities.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;



@Service
public class JwtService {
    
    @Value("${app.jwt.secret}")
    private String secret;

    private final long jwtExpirtation = 15 * 60 * 1000;

    private SecretKey getSigninKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(
                    new Date(
                        System.currentTimeMillis()+ jwtExpirtation
                    )
                )
                .signWith(getSigninKey())
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(getSigninKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
