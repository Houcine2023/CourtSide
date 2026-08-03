package com.courtside.api.dtos;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/** Ask to be told when this exact slot frees up. */
public record WaitlistRequest(
    @NotNull Long courtId,
    @NotNull OffsetDateTime start,
    @NotNull OffsetDateTime end
) {
}
