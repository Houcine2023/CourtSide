package com.courtside.api.dtos;

import java.time.OffsetDateTime;

import com.courtside.api.entities.WaitlistEntry;

public record WaitlistResponse(
    Long id,
    Long courtId,
    String courtName,
    String clubName,
    OffsetDateTime start,
    OffsetDateTime end,
    OffsetDateTime notifiedAt,
    boolean active
) {
    /** Requires court + club to be loaded (the repository fetch-joins them). */
    public static WaitlistResponse from(WaitlistEntry e) {
        return new WaitlistResponse(
            e.getId(),
            e.getCourt().getId(),
            e.getCourt().getName(),
            e.getCourt().getClub().getName(),
            e.getStartTime(),
            e.getEndTime(),
            e.getNotifiedAt(),
            e.isActive()
        );
    }
}
