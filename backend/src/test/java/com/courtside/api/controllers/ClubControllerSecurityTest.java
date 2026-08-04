package com.courtside.api.controllers;

import static com.courtside.api.TestFixtures.admin;
import static com.courtside.api.TestFixtures.club;
import static com.courtside.api.TestFixtures.manager;
import static com.courtside.api.TestFixtures.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 moved the slice annotations into per-technology modules:
// org.springframework.boot.webmvc.test.autoconfigure (was .test.autoconfigure.web.servlet).
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.courtside.api.entities.Club;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.GlobalExceptionHandler;
import com.courtside.api.exceptions.NotFoundException;
import com.courtside.api.repositories.UserRepository;
import com.courtside.api.security.JwtAuthenticationFilter;
import com.courtside.api.security.SecurityConfig;
import com.courtside.api.services.ClubService;
import com.courtside.api.services.JwtService;

/**
 * Web slice: real Spring MVC, real security filter chain, mocked services.
 *
 * This layer exists because @PreAuthorize, the filter chain, bean validation and the
 * @ControllerAdvice are FRAMEWORK behaviour — a unit test cannot see them, and a full
 * integration test would be a slow way to assert a status code.
 *
 * The class below is the security matrix, executable.
 */
@WebMvcTest(ClubController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@DisplayName("ClubController — security matrix and error mapping")
class ClubControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClubService clubService;

    // The filter chain needs these; no token is ever sent, so they are never used.
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserRepository userRepository;

    private static final String VALID_BODY =
            "{\"name\":\"Padel Lac\",\"city\":\"Tunis\",\"address\":\"Berges du Lac\"}";

    /**
     * Authenticates the request exactly the way JwtAuthenticationFilter does in
     * production: our User entity as principal, ROLE_ prefixed authority.
     * @WithMockUser would put a String principal in the context and
     * @AuthenticationPrincipal User would resolve to null.
     */
    private RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    // ---------------- public reads ----------------

    @Test
    @DisplayName("GET /clubs is public")
    void listClubs_anonymous_isAllowed() throws Exception {
        Page<Club> empty = new PageImpl<>(List.of());
        when(clubService.search(any(), any(), any())).thenReturn(empty);

        mvc.perform(get("/api/v1/clubs"))
           .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /clubs/{id} is public")
    void getClub_anonymous_isAllowed() throws Exception {
        when(clubService.getById(1L)).thenReturn(club(1L, manager(10L)));

        mvc.perform(get("/api/v1/clubs/1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name").value("Test Club 1"));
    }

    // ---------------- writes: the matrix ----------------

    @Test
    @DisplayName("POST /clubs without a token → 401 (not 403)")
    void createClub_anonymous_isUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/clubs")
                        .contentType("application/json")
                        .content(VALID_BODY))
           .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /clubs as MEMBER → 403")
    void createClub_asMember_isForbidden() throws Exception {
        mvc.perform(post("/api/v1/clubs").with(as(member(2L)))
                        .contentType("application/json")
                        .content(VALID_BODY))
           .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /clubs as MANAGER → 201")
    void createClub_asManager_isCreated() throws Exception {
        User mgr = manager(10L);
        when(clubService.create(any(), any())).thenReturn(club(1L, mgr));

        mvc.perform(post("/api/v1/clubs").with(as(mgr))
                        .contentType("application/json")
                        .content(VALID_BODY))
           .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DELETE /clubs/{id} as MANAGER → 403 (admin-only endpoint)")
    void deleteClub_asManager_isForbidden() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/clubs/1").with(as(manager(10L))))
           .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /clubs/{id} as ADMIN → 204")
    void deleteClub_asAdmin_isNoContent() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/clubs/1").with(as(admin(99L))))
           .andExpect(status().isNoContent());
    }

    // ---------------- validation & error mapping ----------------

    @Test
    @DisplayName("an invalid body → 400 with per-field errors")
    void createClub_whenBodyInvalid_isBadRequestWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/clubs").with(as(manager(10L)))
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"city\":\"\",\"address\":\"\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.status").value(400))
           .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @DisplayName("a missing club → 404 in the shared error shape")
    void getClub_whenMissing_isNotFound() throws Exception {
        when(clubService.getById(anyLong())).thenThrow(new NotFoundException("Club", 999L));

        mvc.perform(get("/api/v1/clubs/999"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.status").value(404))
           .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("an ownership violation in the service → 403, with no detail leaked")
    void updateClub_whenNotOwner_isForbidden() throws Exception {
        when(clubService.update(anyLong(), any(), any()))
                .thenThrow(new AccessDeniedException("You do not manage this club"));

        mvc.perform(put("/api/v1/clubs/1").with(as(manager(20L)))
                        .contentType("application/json")
                        .content(VALID_BODY))
           .andExpect(status().isForbidden())
           // The internal reason must not reach the caller.
           .andExpect(jsonPath("$.message").value("You are not allowed to perform this action"));
    }
}
