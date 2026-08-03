package com.courtside.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.dtos.DashboardResponse;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.BusinessRuleException;
import com.courtside.api.repositories.DashboardRepository;
import com.courtside.api.repositories.projections.DashboardProjections.Totals;

@Service
public class DashboardService {

    private static final int MAX_RANGE_DAYS = 366;

    private final DashboardRepository dashboardRepository;
    private final ClubService clubService;

    @Value("${app.timezone:Africa/Tunis}")
    private String timezone;

    public DashboardService(DashboardRepository dashboardRepository, ClubService clubService) {
        this.dashboardRepository = dashboardRepository;
        this.clubService = clubService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(Long clubId, LocalDate from, LocalDate to, User currentUser) {
        Club club = clubService.getById(clubId);
        // Revenue figures are private: only the club's own manager (or an admin) sees them.
        clubService.requireCanManage(club, currentUser);

        if (from.isAfter(to)) {
            throw new BusinessRuleException("'from' must be before 'to'");
        }
        // An unbounded range would let one request scan the whole table.
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BusinessRuleException("The range cannot exceed " + MAX_RANGE_DAYS + " days");
        }

        ZoneId zone = ZoneId.of(timezone);
        // Half-open interval [from 00:00, to+1 00:00): the last day is fully included
        // without the classic "23:59:59" hack that silently drops the final second.
        OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        Totals totals = dashboardRepository.totals(clubId, start, end);

        long confirmed = totals == null ? 0 : totals.getConfirmedCount();
        long cancelled = totals == null ? 0 : totals.getCancelledCount();
        BigDecimal revenue = totals == null || totals.getRevenue() == null
                ? BigDecimal.ZERO : totals.getRevenue();

        long attempts = confirmed + cancelled;
        BigDecimal cancellationRate = attempts == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelled * 100.0 / attempts).setScale(1, RoundingMode.HALF_UP);

        List<DashboardResponse.WeekPoint> weeks = dashboardRepository
                .revenueByWeek(clubId, start, end).stream()
                .map(r -> new DashboardResponse.WeekPoint(
                        // Instant -> OffsetDateTime in the club's zone, so the client
                        // sees "week starting Monday 00:00 local", not a UTC shift.
                        r.getWeekStart().atZone(zone).toOffsetDateTime(),
                        r.getBookings(), r.getRevenue(), r.getRunningRevenue()))
                .toList();

        List<DashboardResponse.CourtPerformance> courts = dashboardRepository
                .revenueByCourt(clubId, start, end).stream()
                .map(r -> new DashboardResponse.CourtPerformance(
                        r.getCourtId(), r.getCourtName(), r.getBookings(),
                        r.getRevenue(),
                        r.getRevenueSharePct() == null ? BigDecimal.ZERO : r.getRevenueSharePct()))
                .toList();

        List<DashboardResponse.HourPoint> hours = dashboardRepository
                .busiestHours(clubId, start, end, timezone).stream()
                .map(r -> new DashboardResponse.HourPoint(r.getHourOfDay(), r.getBookings()))
                .toList();

        return new DashboardResponse(
                clubId, from, to,
                new DashboardResponse.Summary(confirmed, cancelled, revenue, cancellationRate),
                weeks, courts, hours);
    }
}
