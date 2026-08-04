package com.courtside.api.services;

import static com.courtside.api.TestFixtures.admin;
import static com.courtside.api.TestFixtures.club;
import static com.courtside.api.TestFixtures.manager;
import static com.courtside.api.TestFixtures.member;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.courtside.api.entities.Club;
import com.courtside.api.entities.User;
import com.courtside.api.repositories.ClubRepository;
import com.courtside.api.repositories.UserRepository;

/**
 * The authorization rule that @PreAuthorize cannot express: roles say WHAT you are,
 * this says whether the object is YOURS. Every row of this matrix is a real attack
 * scenario, which is why it gets its own test class.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClubService.requireCanManage — ownership matrix")
class ClubServiceOwnershipTest {

    @Mock
    private ClubRepository clubRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClubService clubService;

    @Test
    @DisplayName("an ADMIN may manage any club")
    void requireCanManage_whenAdmin_allows() {
        Club someoneElsesClub = club(1L, manager(10L));

        assertThatCode(() -> clubService.requireCanManage(someoneElsesClub, admin(99L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the owning MANAGER may manage their own club")
    void requireCanManage_whenOwningManager_allows() {
        User owner = manager(10L);
        Club ownClub = club(1L, owner);

        assertThatCode(() -> clubService.requireCanManage(ownClub, owner))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a DIFFERENT manager is refused — the whole point of the check")
    void requireCanManage_whenOtherManager_denies() {
        Club ownedByTen = club(1L, manager(10L));
        User intruder = manager(20L);

        // Same role, different person: without this check any manager could edit any
        // club in the platform.
        assertThatThrownBy(() -> clubService.requireCanManage(ownedByTen, intruder))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a MEMBER is refused")
    void requireCanManage_whenMember_denies() {
        Club someClub = club(1L, manager(10L));

        assertThatThrownBy(() -> clubService.requireCanManage(someClub, member(30L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a club with no manager is refused to every manager")
    void requireCanManage_whenClubHasNoManager_deniesManagers() {
        Club orphan = club(1L, null); // manager deleted -> ON DELETE SET NULL

        assertThatThrownBy(() -> clubService.requireCanManage(orphan, manager(10L)))
                .isInstanceOf(AccessDeniedException.class);
        // ...but an admin can still rescue it.
        assertThatCode(() -> clubService.requireCanManage(orphan, admin(99L)))
                .doesNotThrowAnyException();
    }
}
