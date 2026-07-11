package com.courtside.api.dtos;

public record MeResponse(
    String email,
    String fullName,
    String role
) {
}
