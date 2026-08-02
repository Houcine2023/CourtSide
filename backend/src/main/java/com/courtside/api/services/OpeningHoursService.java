package com.courtside.api.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.dtos.OpeningHoursRequest;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.OpeningHours;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.BusinessRuleException;
import com.courtside.api.repositories.OpeningHoursRepository;

@Service
public class OpeningHoursService {

    private final OpeningHoursRepository openingHoursRepository;
    private final ClubService clubService;

    public OpeningHoursService(OpeningHoursRepository openingHoursRepository, ClubService clubService) {
        this.openingHoursRepository = openingHoursRepository;
        this.clubService = clubService;
    }

    @Transactional(readOnly = true)
    public List<OpeningHours> listByClub(Long clubId) {
        clubService.getById(clubId); // 404 if the club does not exist
        return openingHoursRepository.findByClubIdOrderByDayOfWeek(clubId);
    }

    /**
     * Upsert: one row per (club, weekday) — the schema has a UNIQUE constraint on that
     * pair, so "set Monday's hours" must update the existing row rather than insert a
     * duplicate. Doing it explicitly gives a clean API (PUT semantics) instead of
     * letting the database reject the second insert.
     */
    @Transactional
    public OpeningHours setForDay(Long clubId, OpeningHoursRequest request, User currentUser) {
        Club club = clubService.getById(clubId);
        clubService.requireCanManage(club, currentUser);

        // Cross-field validation cannot live in an annotation on a single field.
        if (!request.opens().isBefore(request.closes())) {
            throw new BusinessRuleException("Opening time must be before closing time");
        }

        OpeningHours entity = openingHoursRepository
                .findByClubIdAndDayOfWeek(clubId, request.dayOfWeek())
                .orElseGet(() -> {
                    OpeningHours created = new OpeningHours();
                    created.setClub(club);
                    created.setDayOfWeek(request.dayOfWeek());
                    return created;
                });

        entity.setOpens(request.opens());
        entity.setCloses(request.closes());
        return openingHoursRepository.save(entity);
    }

    /** Removing a day means the club is closed that day. */
    @Transactional
    public void deleteDay(Long clubId, Integer dayOfWeek, User currentUser) {
        Club club = clubService.getById(clubId);
        clubService.requireCanManage(club, currentUser);
        openingHoursRepository.findByClubIdAndDayOfWeek(clubId, dayOfWeek)
                .ifPresent(openingHoursRepository::delete);
    }
}
