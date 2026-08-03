package com.courtside.api.dtos;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Broadcast when a slot is taken or released.
 *
 * Deliberately minimal: no user id, no price, no booking id. Anyone watching a club's
 * topic receives this, so it must contain nothing private — only "this slot changed".
 * Clients react by refreshing that day's grid.
 */
public record AvailabilityEvent(
    Type type,
    Long clubId,
    Long courtId,
    LocalDate date,
    OffsetDateTime slotStart,
    OffsetDateTime slotEnd
) {
    public enum Type {
        SLOT_BOOKED,
        SLOT_RELEASED
    }
}
