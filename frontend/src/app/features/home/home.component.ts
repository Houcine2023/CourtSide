import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { Club, Court } from '../../core/models/api.models';
import { ClubService } from '../../core/services/club.service';
import { AuthService } from '../../core/services/auth.service';
import { ClubPhotoComponent } from '../../shared/club-photo/club-photo.component';

/** A club plus everything the landing cards need to render without further requests. */
interface ClubWithCourts extends Club {
  courts: Court[];
}

/**
 * The landing page.
 *
 * Club discovery is deliberately done in the browser once, then filtered locally,
 * rather than round-tripping to the API on every keystroke. The public /clubs
 * endpoint is small, and instant feedback matters more here than server-side
 * paging — the full searchable catalogue lives on /clubs for large datasets.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink, ClubPhotoComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private readonly clubService = inject(ClubService);
  protected readonly auth = inject(AuthService);

  protected readonly today = new Date().toISOString().split('T')[0];

  /** Hero search box, wired into the finder below. */
  protected readonly query = signal('');
  protected readonly activeCity = signal<string | null>(null);
  protected readonly activeSport = signal<SportFilter | null>(null);

  private readonly feed = toSignal(
    this.clubService.search('', '', 0, 60).pipe(
      switchMap((page) => {
        const clubs = page.content;
        if (clubs.length === 0) {
          return of({ clubs: [] as ClubWithCourts[], failed: false, loaded: true });
        }
        // One request per club, in parallel. Tolerates a single club failing so
        // one bad record cannot empty the whole landing page.
        return forkJoin(
          clubs.map((club) =>
            this.clubService.getCourts(club.id, false).pipe(
              map((courts) => ({ ...club, courts })),
              catchError(() => of({ ...club, courts: [] as Court[] })),
            ),
          ),
        ).pipe(
          map((withCourts) => ({ clubs: withCourts, failed: false, loaded: true })),
        );
      }),
      catchError(() => of({ clubs: [] as ClubWithCourts[], failed: true, loaded: true })),
    ),
    // `loaded` starts false and only flips once a response (or error) lands, so an
    // empty database shows the empty state instead of a skeleton that never ends.
    { initialValue: { clubs: [] as ClubWithCourts[], failed: false, loaded: false } },
  );

  protected readonly loading = computed(() => !this.feed().loaded);
  protected readonly failed = computed(() => this.feed().failed);
  protected readonly allClubs = computed(() => this.feed().clubs);

  /** Distinct cities, most clubs first, so the chips order by usefulness. */
  protected readonly cities = computed(() => {
    const counts = new Map<string, number>();
    for (const club of this.allClubs()) {
      counts.set(club.city, (counts.get(club.city) ?? 0) + 1);
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([city]) => city);
  });

  protected readonly clubs = computed(() => {
    const term = this.query().trim().toLowerCase();
    const city = this.activeCity();
    const sport = this.activeSport();

    return this.allClubs().filter((club) => {
      if (city && club.city !== city) return false;
      if (sport && !club.courts.some((c) => c.sport === sport && c.active)) return false;
      if (!term) return true;
      return (
        club.name.toLowerCase().includes(term) ||
        club.city.toLowerCase().includes(term) ||
        club.address.toLowerCase().includes(term)
      );
    });
  });

  protected readonly hasFilters = computed(
    () => this.query().trim().length > 0 || this.activeCity() !== null || this.activeSport() !== null,
  );

  /** Headline numbers, taken from real rows rather than hardcoded. */
  protected readonly stats = computed(() => {
    const clubs = this.allClubs();
    const courts = clubs.flatMap((c) => c.courts).filter((c) => c.active);
    return {
      clubs: clubs.length,
      courts: courts.length,
      cities: new Set(clubs.map((c) => c.city)).size,
      sports: new Set(courts.map((c) => c.sport)).size,
    };
  });

  protected readonly sports: ReadonlyArray<{ value: SportFilter; label: string; icon: string }> = [
    { value: 'PADEL', label: 'Padel', icon: '🏓' },
    { value: 'TENNIS', label: 'Tennis', icon: '🎾' },
    { value: 'SQUASH', label: 'Squash', icon: '🏸' },
  ];

  protected updateQuery(value: string): void {
    this.query.set(value);
  }

  protected toggleCity(city: string): void {
    this.activeCity.update((current) => (current === city ? null : city));
  }

  protected toggleSport(sport: SportFilter): void {
    this.activeSport.update((current) => (current === sport ? null : sport));
  }

  protected clearFilters(): void {
    this.query.set('');
    this.activeCity.set(null);
    this.activeSport.set(null);
  }

  /** Distinct sports a club actually has active courts for, in a stable order. */
  protected clubSports(club: ClubWithCourts): SportFilter[] {
    const present = new Set(club.courts.filter((c) => c.active).map((c) => c.sport));
    return this.sports.filter((s) => present.has(s.value)).map((s) => s.value);
  }

  protected activeCourtCount(club: ClubWithCourts): number {
    return club.courts.filter((c) => c.active).length;
  }

  /** Deep-links to the first bookable court so the card leads straight to booking. */
  protected firstCourtId(club: ClubWithCourts): number | null {
    return club.courts.find((c) => c.active)?.id ?? null;
  }

  protected sportIcon(sport: SportFilter): string {
    return this.sports.find((s) => s.value === sport)?.icon ?? '🏟️';
  }

  /**
   * The hero search and the finder section are one control in two places: typing
   * in the hero fills the filter and brings it into view, instead of being a
   * decorative input that goes nowhere.
   */
  protected searchFromHero(): void {
    document.getElementById('finder')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    document.getElementById('club-search')?.focus();
  }
}

type SportFilter = 'PADEL' | 'TENNIS' | 'SQUASH';