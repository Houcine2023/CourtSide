package com.courtside.api.dtos;

/**
 * The token pair returned by register / login / refresh.
 *
 * accessToken  — short-lived JWT (15 min), sent with EVERY request.
 * refreshToken — long-lived opaque string (7 days), sent ONLY to /auth/refresh.
 */
public record AuthResponse(
    String accessToken,
    String refreshToken
) {

}
