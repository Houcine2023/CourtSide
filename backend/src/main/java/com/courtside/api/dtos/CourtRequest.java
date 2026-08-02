package com.courtside.api.dtos;

import java.math.BigDecimal;

import com.courtside.api.entities.Sport;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a court.
 *
 * The bounds mirror the CHECK constraints in the Flyway schema on purpose:
 * validation here gives the client a friendly 400 with field names, while the
 * database constraint remains the last line of defence. Two layers, same rule.
 */
public record CourtRequest(
    @NotBlank @Size(max = 80)
    String name,

    @NotNull
    Sport sport,

    @NotNull @Min(30) @Max(240)
    Integer slotMinutes,

    @NotNull @DecimalMin("0.0") @Digits(integer = 6, fraction = 2)
    BigDecimal pricePerSlot
) {
}
