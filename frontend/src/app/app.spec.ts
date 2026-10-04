import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { App } from './app';
import { ClubService } from './core/services/club.service';
import { AuthService } from './core/services/auth.service';
import { Club, Me } from './core/models/api.models';

/**
 * The app shell renders the navbar and the footer around the router outlet, so
 * these tests assert on the navigation contract rather than on markup details.
 *
 * The previous version of this file was the `ng new` scaffold: it asserted the
 * page contained "Hello, frontend" and provided no router at all, so both tests
 * failed once a real navbar replaced the placeholder.
 */

const club = (over: Partial<Club> = {}): Club => ({
  id: 1,
  name: 'Test Club',
  city: 'Tunis',
  address: '1 Test Street',
  managerId: null,
  managerName: null,
  ...over,
});

/** AuthService exposes its state as signals, so the stub has to as well. */
function authStub(user: Me | null) {
  return {
    currentUser: signal<Me | null>(user).asReadonly(),
    isLoggedIn: signal(user !== null).asReadonly(),
    isStaff: signal(user?.role === 'MANAGER' || user?.role === 'ADMIN').asReadonly(),
    displayName: signal(user?.fullName.split(' ')[0] ?? '').asReadonly(),
    loadCurrentUser: vi.fn(() => of(user)),
    logout: vi.fn(),
    clearSession: vi.fn(),
  };
}

const emptyPage = { content: [], page: 0, size: 0, totalElements: 0, totalPages: 0, first: true, last: true };

/**
 * Builds the shell and runs a first change-detection pass. Without detectChanges
 * the @if branches and attribute bindings are still unrendered, so the DOM reads
 * as empty and every assertion below would pass or fail for the wrong reason.
 */
function configure(user: Me | null, managed: Club[] = []) {
  TestBed.configureTestingModule({
    imports: [App],
    providers: [
      // The navbar uses routerLink/routerLinkActive, which need a real router and
      // therefore an ActivatedRoute in the injection context.
      provideRouter([]),
      { provide: AuthService, useValue: authStub(user) },
      {
        provide: ClubService,
        useValue: {
          search: vi.fn(() => of({ ...emptyPage, content: managed, totalElements: managed.length })),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(App);
  fixture.detectChanges();
  return fixture;
}

/**
 * The dashboard link is resolved asynchronously from the clubs the user manages,
 * so asserting on it needs the microtask queue drained first.
 */
async function configureSettled(user: Me | null, managed: Club[] = []) {
  const fixture = configure(user, managed);
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

const text = (fixture: ComponentFixture<App>) =>
  (fixture.nativeElement as HTMLElement).textContent ?? '';

describe('App shell', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('creates the shell', () => {
    expect(configure(null).componentInstance).toBeTruthy();
  });

  it('shows the CourtSide brand', () => {
    expect(text(configure(null))).toContain('CourtSide');
  });

  it('offers sign in and registration to anonymous visitors', () => {
    const content = text(configure(null));
    expect(content).toContain('Sign in');
    expect(content).toContain('Get started');
  });

  it('does not leak manager links to anonymous visitors', () => {
    const content = text(configure(null));
    expect(content).not.toContain('My Clubs');
    expect(content).not.toContain('Dashboard');
  });

  it('greets a signed-in player and offers their own pages', () => {
    const content = text(configure({ id: 7, email: 'p@test.tn', fullName: 'Sara Jouini', role: 'MEMBER' }));
    expect(content).toContain('Sara');
    expect(content).toContain('My bookings');
    expect(content).toContain('My profile');
    expect(content).toContain('Sign out');
  });

  it('withholds manager links from players', () => {
    const content = text(configure({ id: 7, email: 'p@test.tn', fullName: 'Sara Jouini', role: 'MEMBER' }));
    expect(content).not.toContain('My Clubs');
    expect(content).not.toContain('Dashboard');
  });

  it('shows manager links to a manager', async () => {
    const content = text(
      await configureSettled(
        { id: 2, email: 'm@courtside.tn', fullName: 'Mounir Trabelsi', role: 'MANAGER' },
        [club({ id: 9, name: 'Riverside Padel', managerId: 2 })],
      ),
    );
    expect(content).toContain('My Clubs');
    expect(content).toContain('Dashboard');
  });

  it('points the dashboard link at a club the manager owns, not a hardcoded id', async () => {
    // Regression guard: this used to link to /clubs/1/dashboard for every manager,
    // which sent them to a club they did not own and the backend answered 403.
    const fixture = await configureSettled(
      { id: 2, email: 'm@courtside.tn', fullName: 'Mounir Trabelsi', role: 'MANAGER' },
      [club({ id: 9, name: 'Riverside Padel', managerId: 2 })],
    );
    const href = (fixture.nativeElement as HTMLElement)
      .querySelector('a[href*="dashboard"]')
      ?.getAttribute('href');
    expect(href).toBe('/clubs/9/dashboard');
  });

  it('hides the dashboard link when no club could be resolved', async () => {
    // Better to omit the link than to point somewhere the user cannot open.
    const fixture = await configureSettled(
      { id: 2, email: 'm@courtside.tn', fullName: 'Mounir Trabelsi', role: 'MANAGER' },
      [],
    );
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href*="dashboard"]')).toBeNull();
  });

  it('swallows a failed club lookup instead of breaking the navbar', async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authStub({ id: 2, email: 'm@courtside.tn', fullName: 'Mounir Trabelsi', role: 'MANAGER' }) },
        { provide: ClubService, useValue: { search: vi.fn(() => throwError(() => new Error('boom'))) } },
      ],
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.brand')).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href*="dashboard"]')).toBeNull();
  });

  it('gives the hamburger toggle an accessible name and state', () => {
    const el = (configure(null).nativeElement as HTMLElement).querySelector('.nav-toggle');
    expect(el).not.toBeNull();
    expect(el!.getAttribute('aria-label')).toBe('Toggle navigation');
    expect(el!.getAttribute('aria-expanded')).toBe('false');
    expect(el!.getAttribute('aria-controls')).toBe('primary-nav');
  });

  it('exposes a skip link as the first tab stop', () => {
    const el = (configure(null).nativeElement as HTMLElement).querySelector('.skip-link');
    expect(el?.getAttribute('href')).toBe('#main');
  });
});