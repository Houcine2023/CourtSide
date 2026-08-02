package com.courtside.api.dtos;

import java.time.LocalTime;

import com.courtside.api.entities.OpeningHours;

public record OpeningHoursResponse(
    Long id,
    Integer dayOfWeek,
    LocalTime opens,
    LocalTime closes
) {
    public static OpeningHoursResponse from(OpeningHours oh) {
        return new OpeningHoursResponse(oh.getId(), oh.getDayOfWeek(), oh.getOpens(), oh.getCloses());
    }
}
