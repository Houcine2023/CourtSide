package com.courtside.api.dtos;

import jakarta.validation.constraints.NotBlank;

/** Body of /auth/refresh and /auth/logout: the raw refresh token. */
public record RefreshRequest(
    @NotBlank
    String refreshToken
) {

}
