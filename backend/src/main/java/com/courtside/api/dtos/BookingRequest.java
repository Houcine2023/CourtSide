package com.courtside.api.dtos;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * What the client may decide: which court, from when, to when.
 *
 * Deliberately absent: the price and the user. The price is computed server-side from
 * the court (never trust a client-sent amount — that is how you get 0.01 DT bookings),
 * and the user comes from the authenticated principal, so nobody can book on someone
 * else's behalf.
 */
public record BookingRequest(
    @NotNull
    Long courtId,

    @NotNull
    OffsetDateTime start,

    @NotNull
    OffsetDateTime end
) {
}
