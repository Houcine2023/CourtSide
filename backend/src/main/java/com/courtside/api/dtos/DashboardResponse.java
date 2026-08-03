package com.courtside.api.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Everything a club manager sees on their dashboard for a given period. */
public record DashboardResponse(
    Long clubId,
    LocalDate from,
    LocalDate to,
    Summary summary,
    List<WeekPoint> revenueByWeek,
    List<CourtPerformance> byCourt,
    List<HourPoint> busiestHours
) {
    public record Summary(
        long confirmedBookings,
        long cancelledBookings,
        BigDecimal revenue,
        /** Cancellations / total requests, in percent — a health signal for the club. */
        BigDecimal cancellationRatePct,
        /** Slots the club offered over the period (derived from opening hours). */
        long capacitySlots,
        /** Booked / offered, in percent — the number a club owner actually cares about. */
        BigDecimal occupancyRatePct
    ) {
    }

    public record WeekPoint(
        OffsetDateTime weekStart,
        long bookings,
        BigDecimal revenue,
        BigDecimal runningRevenue
    ) {
    }

    public record CourtPerformance(
        Long courtId,
        String courtName,
        long bookings,
        BigDecimal revenue,
        BigDecimal revenueSharePct
    ) {
    }

    public record HourPoint(int hourOfDay, long bookings) {
    }
}
