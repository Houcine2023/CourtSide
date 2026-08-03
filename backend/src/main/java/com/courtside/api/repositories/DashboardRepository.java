package com.courtside.api.repositories;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.courtside.api.entities.Booking;
import com.courtside.api.repositories.projections.DashboardProjections.CourtRow;
import com.courtside.api.repositories.projections.DashboardProjections.HourRow;
import com.courtside.api.repositories.projections.DashboardProjections.Totals;
import com.courtside.api.repositories.projections.DashboardProjections.WeeklyRow;

/**
 * Reporting queries. Native SQL on purpose: window functions, FILTER and date_trunc
 * are where PostgreSQL shines, and JPQL cannot express them. The trade-off is
 * portability (this file is Postgres-specific) for power and speed — a deliberate
 * choice for read-only analytics, never for the write path.
 *
 * extends Repository (not JpaRepository): this is a query-only repository, so it
 * exposes nothing else — no save(), no deleteAll(). Least privilege applies to APIs too.
 */
public interface DashboardRepository extends Repository<Booking, Long> {

    /**
     * FILTER (WHERE ...) is a cleaner PostgreSQL alternative to
     * COUNT(CASE WHEN ... THEN 1 END): several different aggregates over the same
     * rows in a single pass over the table.
     */
    @Query(value = """
            SELECT
              COUNT(*) FILTER (WHERE b.status IN ('CONFIRMED','NO_SHOW'))            AS "confirmedCount",
              COUNT(*) FILTER (WHERE b.status = 'CANCELLED')                          AS "cancelledCount",
              COALESCE(SUM(b.price) FILTER (WHERE b.status IN ('CONFIRMED','NO_SHOW')), 0) AS "revenue"
            FROM bookings b
            JOIN courts c ON c.id = b.court_id
            WHERE c.club_id = :clubId
              AND b.start_time >= :from
              AND b.start_time < :to
            """, nativeQuery = true)
    Totals totals(Long clubId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Revenue per week WITH a running total.
     *
     * `SUM(SUM(b.price)) OVER (ORDER BY ...)` looks strange but is exact: the inner
     * SUM aggregates each week's revenue, the outer SUM is a WINDOW function that
     * accumulates those weekly totals in order. Window functions run AFTER GROUP BY,
     * which is why they can take an aggregate as input.
     */
    @Query(value = """
            SELECT
              date_trunc('week', b.start_time)                     AS "weekStart",
              COUNT(*)                                             AS "bookings",
              SUM(b.price)                                         AS "revenue",
              SUM(SUM(b.price)) OVER (ORDER BY date_trunc('week', b.start_time)) AS "runningRevenue"
            FROM bookings b
            JOIN courts c ON c.id = b.court_id
            WHERE c.club_id = :clubId
              AND b.status IN ('CONFIRMED','NO_SHOW')
              AND b.start_time >= :from
              AND b.start_time < :to
            GROUP BY date_trunc('week', b.start_time)
            ORDER BY 1
            """, nativeQuery = true)
    List<WeeklyRow> revenueByWeek(Long clubId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Per-court performance with each court's share of the club total.
     *
     * `SUM(SUM(revenue)) OVER ()` — an empty OVER() means "the whole result set",
     * i.e. the grand total, available on every row without a second query or a
     * subquery. This is THE idiomatic way to compute percentages of a total in SQL.
     *
     * LEFT JOIN keeps courts with zero bookings visible (a court nobody books is
     * exactly what a manager needs to see), and NULLIF guards against division by zero.
     */
    @Query(value = """
            SELECT
              c.id                                   AS "courtId",
              c.name                                 AS "courtName",
              COUNT(b.id)                            AS "bookings",
              COALESCE(SUM(b.price), 0)              AS "revenue",
              ROUND(
                100.0 * COALESCE(SUM(b.price), 0)
                / NULLIF(SUM(COALESCE(SUM(b.price), 0)) OVER (), 0)
              , 1)                                   AS "revenueSharePct"
            FROM courts c
            LEFT JOIN bookings b
              ON b.court_id = c.id
             AND b.status IN ('CONFIRMED','NO_SHOW')
             AND b.start_time >= :from
             AND b.start_time < :to
            WHERE c.club_id = :clubId
            GROUP BY c.id, c.name
            ORDER BY "revenue" DESC, c.name
            """, nativeQuery = true)
    List<CourtRow> revenueByCourt(Long clubId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Busiest hours, in the club's local time — `AT TIME ZONE` converts the stored
     * instant to wall-clock before extracting the hour, otherwise a 20:00 booking in
     * Tunis would be reported as 19:00 (UTC).
     */
    @Query(value = """
            SELECT
              EXTRACT(HOUR FROM b.start_time AT TIME ZONE :zone)::int AS "hourOfDay",
              COUNT(*)                                                AS "bookings"
            FROM bookings b
            JOIN courts c ON c.id = b.court_id
            WHERE c.club_id = :clubId
              AND b.status IN ('CONFIRMED','NO_SHOW')
              AND b.start_time >= :from
              AND b.start_time < :to
            GROUP BY 1
            ORDER BY "bookings" DESC, 1
            LIMIT 5
            """, nativeQuery = true)
    List<HourRow> busiestHours(Long clubId, OffsetDateTime from, OffsetDateTime to, String zone);
}
