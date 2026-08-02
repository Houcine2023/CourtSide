package com.courtside.api.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.dtos.CourtRequest;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.Court;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.NotFoundException;
import com.courtside.api.repositories.CourtRepository;

@Service
public class CourtService {

    private final CourtRepository courtRepository;
    private final ClubService clubService;

    public CourtService(CourtRepository courtRepository, ClubService clubService) {
        this.courtRepository = courtRepository;
        this.clubService = clubService;
    }

    // ---------------- reads ----------------

    /**
     * Public listing shows only ACTIVE courts; staff see everything, including the
     * deactivated ones they may want to bring back.
     */
    @Transactional(readOnly = true)
    public List<Court> listByClub(Long clubId, boolean includeInactive) {
        clubService.getById(clubId); // 404 if the club itself does not exist
        return includeInactive
                ? courtRepository.findByClubIdOrderByName(clubId)
                : courtRepository.findByClubIdAndActiveTrueOrderByName(clubId);
    }

    @Transactional(readOnly = true)
    public Court getById(Long id) {
        return courtRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Court", id));
    }

    /**
     * Court WITH its club loaded. Use this whenever the caller will touch
     * court.getClub() after the transaction ends (DTO mapping), otherwise the lazy
     * proxy throws LazyInitializationException — open-in-view is off by design.
     */
    @Transactional(readOnly = true)
    public Court getByIdWithClub(Long id) {
        return courtRepository.findByIdWithClubAndManager(id)
                .orElseThrow(() -> new NotFoundException("Court", id));
    }

    // ---------------- writes ----------------

    @Transactional
    public Court create(Long clubId, CourtRequest request, User currentUser) {
        Club club = clubService.getById(clubId);
        clubService.requireCanManage(club, currentUser); // court permissions follow the club

        Court court = new Court();
        court.setClub(club);
        apply(request, court);
        return courtRepository.save(court);
    }

    @Transactional
    public Court update(Long id, CourtRequest request, User currentUser) {
        // One query brings court + club + manager: the ownership check below needs
        // all three, and lazy proxies would each cost an extra SELECT.
        Court court = courtRepository.findByIdWithClubAndManager(id)
                .orElseThrow(() -> new NotFoundException("Court", id));

        clubService.requireCanManage(court.getClub(), currentUser);
        apply(request, court);
        return court; // managed entity -> flushed on commit
    }

    /**
     * "Delete" = deactivate. Courts carry booking history through a cascading FK;
     * a real DELETE would erase past bookings, which is data loss disguised as a
     * feature. Deactivating hides the court from booking flows and keeps the record.
     */
    @Transactional
    public void deactivate(Long id, User currentUser) {
        Court court = courtRepository.findByIdWithClubAndManager(id)
                .orElseThrow(() -> new NotFoundException("Court", id));

        clubService.requireCanManage(court.getClub(), currentUser);
        court.setActive(false);
    }

    private void apply(CourtRequest request, Court court) {
        court.setName(request.name());
        court.setSport(request.sport());
        court.setSlotMinutes(request.slotMinutes());
        court.setPricePerSlot(request.pricePerSlot());
    }
}
