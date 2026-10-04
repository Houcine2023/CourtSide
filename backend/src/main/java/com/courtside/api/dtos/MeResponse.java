package com.courtside.api.dtos;

public record MeResponse(
    Long id,
    String email,
    String fullName,
    String role
) {
}
