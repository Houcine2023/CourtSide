package com.courtside.api.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.courtside.api.dtos.ClubRequest;
import com.courtside.api.dtos.ClubResponse;
import com.courtside.api.dtos.PageResponse;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.ClubPhoto;
import com.courtside.api.entities.User;
import com.courtside.api.services.ClubService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    /**
     * Public search. Note the hard cap on `size`: without it a client could ask for
     * size=1000000 and turn a paginated endpoint into a denial-of-service vector.
     */
    @GetMapping
    public PageResponse<ClubResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Club> result = clubService.search(q, city,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("name")));
        return PageResponse.from(result, ClubResponse::from);
    }

    @GetMapping("/{id}")
    public ClubResponse getOne(@PathVariable Long id) {
        return ClubResponse.from(clubService.getById(id));
    }

    /**
     * Public, because the club cards that show it are public. SecurityConfig already
     * permits GET /api/v1/clubs/**, so the endpoint's own protection is the response
     * hardening below rather than an authentication requirement.
     *
     * Two headers do the work here:
     * - nosniff stops the browser from re-interpreting the bytes as HTML if the
     *   stored content type turned out to be wrong.
     * - the sandboxing CSP blocks script execution when someone opens the URL
     *   directly, which matters because SVG is user-uploaded and IS a document.
     *   Neither header affects the normal <img> rendering path.
     *
     * no-store rather than a max-age: the URL is stable, so a cached copy would
     * still be served after a manager replaced the photo.
     */
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long id) {
        ClubPhoto photo = clubService.getPhoto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .body(photo.getData());
    }

    /**
     * @PreAuthorize runs BEFORE the method body, so unauthorised callers never reach
     * the service. 'hasAnyRole' prepends "ROLE_" automatically — which is why the JWT
     * filter builds authorities as "ROLE_" + role name.
     *
     * @AuthenticationPrincipal injects what the filter put in the SecurityContext
     * (our User entity) — cleaner and more testable than reaching for
     * SecurityContextHolder inside the method.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ClubResponse create(
            @Valid @RequestBody ClubRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ClubResponse.from(clubService.create(request, currentUser));
    }

    /** Role checked here; OWNERSHIP (is it your club?) is checked in the service. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ClubResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ClubRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ClubResponse.from(clubService.update(id, request, currentUser));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        clubService.delete(id, currentUser);
    }

    /**
     * Multipart upload, replacing any photo already stored. The `file` part name is
     * what the frontend sends; Spring rejects a request without it before the method
     * body runs.
     *
     * Role here, ownership in the service — the same two-layer rule as update().
     */
    @PutMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void updatePhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser
    ) {
        clubService.updatePhoto(id, file, currentUser);
    }

    @DeleteMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void deletePhoto(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        clubService.deletePhoto(id, currentUser);
    }
}
