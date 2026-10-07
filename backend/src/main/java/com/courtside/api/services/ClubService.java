package com.courtside.api.services;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.courtside.api.dtos.ClubRequest;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.ClubPhoto;
import com.courtside.api.entities.Role;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.InvalidPhotoException;
import com.courtside.api.exceptions.NotFoundException;
import com.courtside.api.repositories.ClubPhotoRepository;
import com.courtside.api.repositories.ClubRepository;
import com.courtside.api.repositories.UserRepository;

@Service
public class ClubService {

    /** JPEG, PNG, WebP, GIF and SVG. Anything else is rejected before it is stored. */
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml");

    /** Matches spring.servlet.multipart.max-file-size — see application.yml. */
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ClubPhotoRepository clubPhotoRepository;

    public ClubService(ClubRepository clubRepository, UserRepository userRepository,
            ClubPhotoRepository clubPhotoRepository) {
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.clubPhotoRepository = clubPhotoRepository;
    }

    // ---------------- reads (public) ----------------

    /**
     * readOnly = true: no dirty-check snapshots, and the driver can route the query
     * to a read replica in a scaled setup. Free performance for query methods.
     */
    @Transactional(readOnly = true)
    public Page<Club> search(String q, String city, Pageable pageable) {
        // Normalise so the repository never receives NULL parameters (see ClubRepository):
        // no filter on name -> match everything with "%", no city filter -> empty string.
        String namePattern = (q == null || q.isBlank()) ? "%" : "%" + q.trim() + "%";
        String cityFilter = (city == null || city.isBlank()) ? "" : city.trim();
        return clubRepository.search(namePattern, cityFilter, pageable);
    }

    /** Club + manager in one query: the DTO is built after the transaction closes. */
    @Transactional(readOnly = true)
    public Club getById(Long id) {
        return clubRepository.findByIdWithManager(id)
                .orElseThrow(() -> new NotFoundException("Club", id));
    }

    // ---------------- writes (ADMIN, or the owning MANAGER) ----------------

    @Transactional
    public Club create(ClubRequest request, User currentUser) {
        Club club = new Club();
        club.setName(request.name());
        club.setCity(request.city());
        club.setAddress(request.address());
        club.setManager(resolveManager(request.managerId(), currentUser));
        return clubRepository.save(club);
    }

    @Transactional
    public Club update(Long id, ClubRequest request, User currentUser) {
        Club club = getById(id);
        requireCanManage(club, currentUser);

        club.setName(request.name());
        club.setCity(request.city());
        club.setAddress(request.address());

        // Only an ADMIN may (re)assign the manager, and only when managerId is actually
        // present. Absent field = "leave it as it is" — treating absent as "set to null"
        // would silently unassign the manager on every ordinary edit (a bug this very
        // test suite caught: the admin renamed a club and the owner lost their club).
        if (currentUser.getRole() == Role.ADMIN && request.managerId() != null) {
            club.setManager(resolveManager(request.managerId(), currentUser));
        }

        // No save() call: `club` is a managed entity inside this transaction, so
        // Hibernate's dirty checking flushes the changes on commit.
        return club;
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        Club club = getById(id);
        requireCanManage(club, currentUser);
        // Hard delete: the schema cascades to courts (and their bookings). Acceptable
        // for a club that closes; if we ever need history, this becomes a soft delete
        // exactly like Court.active.
        clubRepository.delete(club);
    }

    // ---------------- photos (public read; ADMIN or the owning MANAGER to write) ----------------

    /**
     * The photo row and nothing else. club_photos is keyed by club id, so this is a
     * primary-key read and never touches the Club aggregate.
     */
    @Transactional(readOnly = true)
    public ClubPhoto getPhoto(Long clubId) {
        return clubPhotoRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("Photo for club " + clubId + " not found"));
    }

    /** Upload, or replace the photo that is already there. */
    @Transactional
    public void updatePhoto(Long clubId, MultipartFile file, User currentUser) {
        // Club first: an unknown club is a 404, and it also gives requireCanManage
        // the ownership data it needs before a single byte is read.
        Club club = getById(clubId);
        requireCanManage(club, currentUser);
        validatePhoto(file);

        ClubPhoto photo = clubPhotoRepository.findById(clubId).orElseGet(ClubPhoto::new);
        photo.setClubId(clubId);
        photo.setContentType(file.getContentType());
        try {
            photo.setData(file.getBytes());
        } catch (IOException e) {
            // The upload stream failed after Spring had already accepted the part.
            throw new InvalidPhotoException("The uploaded photo could not be read");
        }
        clubPhotoRepository.save(photo);
    }

    /**
     * Idempotent on purpose: removing a photo that was never there is still the
     * state the caller asked for, so it answers 204 rather than 404.
     */
    @Transactional
    public void deletePhoto(Long clubId, User currentUser) {
        Club club = getById(clubId);
        requireCanManage(club, currentUser);
        clubPhotoRepository.findById(clubId).ifPresent(clubPhotoRepository::delete);
    }

    private void validatePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidPhotoException("No photo file was received");
        }

        // Check the type we are about to STORE, not merely the one that arrived:
        // content_type is echoed back on GET, so it must never be able to name
        // something executable. Serving also sets nosniff and a sandboxing CSP,
        // which is what makes a wrong value harmless rather than dangerous.
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_PHOTO_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidPhotoException("Photo must be a JPEG, PNG, WebP, GIF or SVG image");
        }

        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new InvalidPhotoException("Photo must be 5 MB or smaller");
        }
    }

    // ---------------- authorization helpers ----------------

    /**
     * The ownership rule that @PreAuthorize cannot express: a MANAGER may only touch
     * the club they actually manage.
     *
     * Roles answer "what kind of user are you?" (checked at the endpoint);
     * ownership answers "is this YOUR object?" (checked here, where we have the data).
     * Both layers are needed — role alone would let any manager edit any club.
     */
    public void requireCanManage(Club club, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        boolean isOwningManager = club.getManager() != null
                && club.getManager().getId().equals(currentUser.getId());

        if (!isOwningManager) {
            // 403, not 404: the caller is authenticated and the club exists —
            // they simply may not act on it.
            throw new AccessDeniedException("You do not manage this club");
        }
    }

    private User resolveManager(Long managerId, User currentUser) {
        if (managerId == null) {
            // A manager creating a club becomes its manager by default.
            return currentUser.getRole() == Role.MANAGER ? currentUser : null;
        }
        return userRepository.findById(managerId)
                .orElseThrow(() -> new NotFoundException("User", managerId));
    }
}
