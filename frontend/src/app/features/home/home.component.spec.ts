import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { HomeComponent } from './home.component';
import { ClubService } from '../../core/services/club.service';
import { AuthService } from '../../core/services/auth.service';
import { Club, Court } from '../../core/models/api.models';

/**
 * The landing page derives everything the cards render from two API calls, and
 * all of the interesting behaviour - filtering, ordering, the headline numbers,
 * and the difference between "still loading" and "nothing to show" - lives in
 * computed signals over that data. These tests pin that derivation down without
 * touching the network.
 */

const club = (over: Partial<Club> = {}): Club => ({
  id: 1,
  name: 'Test Club',
  city: 'Tunis',
  address: '1 Test Street',
  managerId: null,
  managerName: null,
  photoUrl: '/api/v1/clubs/1/photo',
  ...over,
});

const court = (over: Partial<Court> = {}): Court => ({
  id: 100,
  clubId: 1,
  name: 'Court 1',
  sport: 'PADEL',
  slotMinutes: 90,
  pricePerSlot: 35,
  active: true,
  ...over,
});

const page = (content: Club[]) => ({
  content,
  page: 0,
  size: content.length,
  totalElements: content.length,
  totalPages: 1,
  first: true,
  last: true,
});

/**
 * Mirrors the component's own load: clubs first, then each club's courts.
 * `courtsByClub` supplies the per-club court lists; a club missing from the map
 * gets an empty list, which is also how the component behaves when one club's
 * courts request fails.
 */
function render(clubs: Club[], courtsByClub: Record<number, Court[]> = {}) {
  TestBed.configureTestingModule({
    imports: [HomeComponent],
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { isLoggedIn: () => false, displayName: () => '' } },
      {
        provide: ClubService,
        useValue: {
          search: vi.fn(() => of(page(clubs))),
          getCourts: vi.fn((id: number) => of(courtsByClub[id] ?? [])),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(HomeComponent);
  fixture.detectChanges();
  return fixture.componentInstance;
}

/** The component keeps these protected; tests read them through the DOM-facing API. */
const names = (c: HomeComponent) => c['clubs']().map((x) => x.name);

describe('HomeComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  describe('filtering', () => {
    const clubs = [
      club({ id: 1, name: 'Tennis Club La Marsa', city: 'La Marsa', address: 'Rue du Port' }),
      club({ id: 2, name: 'Padel Club Tunis', city: 'Tunis', address: 'Avenue Habib' }),
      club({ id: 3, name: 'Squash Center Sfax', city: 'Sfax', address: 'Rue Colbert' }),
    ];
    const courts = {
      1: [court({ id: 11, clubId: 1, sport: 'TENNIS' })],
      2: [court({ id: 21, clubId: 2, sport: 'PADEL' })],
      3: [court({ id: 31, clubId: 3, sport: 'SQUASH' })],
    };

    it('shows every club when nothing is filtered', () => {
      expect(names(render(clubs, courts))).toHaveLength(3);
    });

    it('matches on club name, case-insensitively', () => {
      const c = render(clubs, courts);
      c['updateQuery']('LA MARS');
      expect(names(c)).toEqual(['Tennis Club La Marsa']);
    });

    it('matches on city', () => {
      const c = render(clubs, courts);
      c['updateQuery']('sfax');
      expect(names(c)).toEqual(['Squash Center Sfax']);
    });

    it('matches on address', () => {
      const c = render(clubs, courts);
      c['updateQuery']('colbert');
      expect(names(c)).toEqual(['Squash Center Sfax']);
    });

    it('ignores surrounding whitespace', () => {
      const c = render(clubs, courts);
      c['updateQuery']('   tunis   ');
      expect(names(c)).toEqual(['Padel Club Tunis']);
    });

    it('combines a city filter with a sport filter', () => {
      const c = render(clubs, courts);
      c['toggleCity']('La Marsa');
      c['toggleSport']('TENNIS');
      expect(names(c)).toEqual(['Tennis Club La Marsa']);
    });

    it('returns nothing when city and sport disagree', () => {
      const c = render(clubs, courts);
      c['toggleCity']('Sfax');
      c['toggleSport']('PADEL');
      expect(names(c)).toEqual([]);
    });

    it('toggles a chip back off when selected twice', () => {
      const c = render(clubs, courts);
      c['toggleCity']('Sfax');
      expect(c['clubs']()).toHaveLength(1);
      c['toggleCity']('Sfax');
      expect(c['clubs']()).toHaveLength(3);
    });

    it('clears every filter at once', () => {
      const c = render(clubs, courts);
      c['updateQuery']('s');
      c['toggleCity']('Sfax');
      c['toggleSport']('SQUASH');
      expect(c['hasFilters']()).toBe(true);

      c['clearFilters']();
      expect(c['hasFilters']()).toBe(false);
      expect(names(c)).toHaveLength(3);
    });

    it('does not match a sport against a club that has no such court', () => {
      const c = render([club({ id: 9, name: 'Empty Club', city: 'Tunis' })], { 9: [] });
      c['toggleSport']('PADEL');
      expect(names(c)).toEqual([]);
    });
  });

  describe('inactive courts', () => {
    it('are ignored by the sport filter', () => {
      // A deactivated squash court must not make a club look bookable.
      const c = render([club({ id: 4, name: 'Tennis Only', city: 'Tunis' })], {
        4: [court({ id: 41, sport: 'TENNIS' }), court({ id: 42, sport: 'SQUASH', active: false })],
      });
      c['toggleSport']('SQUASH');
      expect(names(c)).toEqual([]);

      c['toggleSport']('SQUASH');
      c['toggleSport']('TENNIS');
      expect(names(c)).toEqual(['Tennis Only']);
    });

    it('are excluded from the court count and the sport badges', () => {
      const target = render([club({ id: 4 })], {
        4: [court({ id: 41, sport: 'TENNIS' }), court({ id: 42, sport: 'SQUASH', active: false })],
      });
      const loaded = target['allClubs']()[0];
      expect(target['activeCourtCount'](loaded)).toBe(1);
      expect(target['clubSports'](loaded)).toEqual(['TENNIS']);
    });
  });

  describe('headline statistics', () => {
    it('count real rows and only active courts', () => {
      const c = render(
        [
          club({ id: 1, city: 'Tunis' }),
          club({ id: 2, city: 'Sousse' }),
          club({ id: 3, city: 'Tunis' }),
        ],
        {
          1: [court({ id: 11, sport: 'PADEL' }), court({ id: 12, sport: 'TENNIS' })],
          2: [court({ id: 21, sport: 'SQUASH', active: false })],
          3: [court({ id: 31, sport: 'PADEL' })],
        },
      );
      expect(c['stats']()).toEqual({ clubs: 3, courts: 3, cities: 2, sports: 2 });
    });

    it('does not double-count the same city', () => {
      const c = render([club({ id: 1, city: 'Tunis' }), club({ id: 2, city: 'Tunis' })], {
        1: [court({ id: 11 })],
        2: [court({ id: 21 })],
      });
      expect(c['stats']().cities).toBe(1);
    });
  });

  describe('city chips', () => {
    it('are distinct and ordered by how many clubs each city has', () => {
      const c = render(
        [
          club({ id: 1, city: 'Tunis' }),
          club({ id: 2, city: 'Sousse' }),
          club({ id: 3, city: 'Tunis' }),
          club({ id: 4, city: 'Sfax' }),
        ],
        { 1: [], 2: [], 3: [], 4: [] },
      );
      // Tunis (2) first, then Sfax and Sousse alphabetically among the singletons.
      expect(c['cities']()).toEqual(['Tunis', 'Sfax', 'Sousse']);
    });
  });

  describe('loading and failure states', () => {
    it('stops loading on an empty result instead of showing a skeleton forever', () => {
      // Regression guard: loading used to be derived from `clubs.length > 0`, which
      // is indistinguishable from "still in flight" when the catalogue is empty.
      const c = render([]);
      expect(c['loading']()).toBe(false);
      expect(c['failed']()).toBe(false);
      expect(c['clubs']()).toEqual([]);
    });

    it('reports failure when the club request fails', () => {
      TestBed.configureTestingModule({
        imports: [HomeComponent],
        providers: [
          provideRouter([]),
          { provide: AuthService, useValue: { isLoggedIn: () => false, displayName: () => '' } },
          { provide: ClubService, useValue: { search: vi.fn(() => throwError(() => new Error('boom'))) } },
        ],
      });
      const c = TestBed.createComponent(HomeComponent).componentInstance;
      expect(c['loading']()).toBe(false);
      expect(c['failed']()).toBe(true);
    });

    it('keeps other clubs when one club courts request fails', () => {
      // A single bad record must not empty the whole landing page.
      TestBed.configureTestingModule({
        imports: [HomeComponent],
        providers: [
          provideRouter([]),
          { provide: AuthService, useValue: { isLoggedIn: () => false, displayName: () => '' } },
          {
            provide: ClubService,
            useValue: {
              search: vi.fn(() => of(page([club({ id: 1 }), club({ id: 2 })]))),
              getCourts: vi.fn((id: number) => (id === 2 ? throwError(() => new Error('boom')) : of([court({ id: 11 })]))),
            },
          },
        ],
      });
      const c = TestBed.createComponent(HomeComponent).componentInstance;
      expect(c['failed']()).toBe(false);
      expect(c['allClubs']()).toHaveLength(2);
      expect(c['activeCourtCount'](c['allClubs']()[1])).toBe(0);
    });
  });

  describe('card helpers', () => {
    it('links to the first active court', () => {
      const c = render([club({ id: 1 })], {
        1: [court({ id: 55, active: false }), court({ id: 56, active: true })],
      });
      expect(c['firstCourtId'](c['allClubs']()[0])).toBe(56);
    });

    it('has no booking target for a club with no active courts', () => {
      const c = render([club({ id: 1 })], { 1: [court({ id: 55, active: false })] });
      expect(c['firstCourtId'](c['allClubs']()[0])).toBeNull();
    });
  });

  it('renders no club cards while still loading', () => {
    TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isLoggedIn: () => false, displayName: () => '' } },
        { provide: ClubService, useValue: { search: vi.fn(() => of(page([]))), getCourts: vi.fn() } },
      ],
    });
    const fixture: ComponentFixture<HomeComponent> = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    // Empty catalogue: the empty state must render, not the loading skeleton.
    expect(fixture.nativeElement.querySelector('.club-card')).toBeNull();
  });
});