package com.courtside.api.repositories.projections;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Interface projections: Spring Data builds a lightweight proxy per row, mapping each
 * getter to the matching column alias (getWeekStart -> week_start). No entity, no
 * persistence context, no DTO constructor to keep in sync — the right tool for
 * read-only reporting queries.
 */
public class DashboardProjections {

    private DashboardProjections() {
    }

    public interface Totals {
        long getConfirmedCount();
        long getCancelledCount();
        BigDecimal getRevenue();
    }

    public interface WeeklyRow {
        /**
         * Instant, not OffsetDateTime: the JDBC driver hands a TIMESTAMPTZ back as an
         * Instant, and projections do no implicit conversion — declaring the wrong type
         * fails at runtime with "Cannot project java.time.Instant to ...".
         * The service attaches the club's zone when building the response.
         */
        Instant getWeekStart();
        long getBookings();
        BigDecimal getRevenue();
        /** Cumulative revenue since the first week — computed by a window function. */
        BigDecimal getRunningRevenue();
    }

    public interface CourtRow {
        Long getCourtId();
        String getCourtName();
        long getBookings();
        BigDecimal getRevenue();
        /** This court's share of the club's revenue, in percent. */
        BigDecimal getRevenueSharePct();
    }

    public interface HourRow {
        int getHourOfDay();
        long getBookings();
    }
}
