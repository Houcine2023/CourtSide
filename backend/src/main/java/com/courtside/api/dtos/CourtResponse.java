package com.courtside.api.dtos;

import java.math.BigDecimal;

import com.courtside.api.entities.Court;

public record CourtResponse(
    Long id,
    Long clubId,
    String name,
    String sport,
    Integer slotMinutes,
    BigDecimal pricePerSlot,
    boolean active
) {
    /** club is LAZY: getClub().getId() is safe (the id lives in the FK column, no query). */
    public static CourtResponse from(Court court) {
        return new CourtResponse(
            court.getId(),
            court.getClub().getId(),
            court.getName(),
            court.getSport().name(),
            court.getSlotMinutes(),
            court.getPricePerSlot(),
            court.isActive()
        );
    }
}
