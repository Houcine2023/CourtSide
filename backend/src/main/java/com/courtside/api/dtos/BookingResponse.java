package com.courtside.api.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.courtside.api.entities.Booking;

public record BookingResponse(
    Long id,
    Long courtId,
    String courtName,
    Long clubId,
    String clubName,
    OffsetDateTime start,
    OffsetDateTime end,
    String status,
    BigDecimal price
) {
    /** Requires court and club to be loaded (see BookingRepository fetch joins). */
    public static BookingResponse from(Booking b) {
        return new BookingResponse(
            b.getId(),
            b.getCourt().getId(),
            b.getCourt().getName(),
            b.getCourt().getClub().getId(),
            b.getCourt().getClub().getName(),
            b.getStartTime(),
            b.getEndTime(),
            b.getStatus().name(),
            b.getPrice()
        );
    }
}
