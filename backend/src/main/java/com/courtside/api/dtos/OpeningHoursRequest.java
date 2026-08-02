package com.courtside.api.dtos;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One weekday's opening window. Cross-field rule (opens < closes) is checked in the service. */
public record OpeningHoursRequest(
    @NotNull @Min(1) @Max(7)
    Integer dayOfWeek,

    @NotNull
    LocalTime opens,

    @NotNull
    LocalTime closes
) {
}
