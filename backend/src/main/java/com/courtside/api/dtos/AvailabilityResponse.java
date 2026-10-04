package com.courtside.api.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The availability grid of one court for one day.
 *
 * `slots` is always the FULL day (open to close); each slot carries its own
 * `available` flag rather than being omitted. The UI needs to draw taken slots
 * greyed out, not to guess what is missing.
 */
public record AvailabilityResponse(
    Long courtId,
    Long clubId,
    String courtName,
    LocalDate date,
    boolean clubOpen,
    List<Slot> slots
) {
    public record Slot(
        OffsetDateTime start,
        OffsetDateTime end,
        boolean available,
        BigDecimal price
    ) {
    }
}
