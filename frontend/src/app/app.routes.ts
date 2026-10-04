import { Routes } from '@angular/router';

import { authGuard, staffGuard } from './core/guards/auth.guard';

/**
 * Every route is LAZY (`loadComponent`): the initial bundle carries the shell and
 * nothing else, and each page is fetched on first visit. This is the same discipline
 * that took the CED portal from 3.8 MB to 316 KB — applied from the first commit here
 * rather than retrofitted.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'CourtSide — book a padel, tennis or squash court in seconds',
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'clubs',
    title: 'Clubs — CourtSide',
    loadComponent: () =>
      import('./features/clubs/club-list/club-list.component').then((m) => m.ClubListComponent),
  },
  {
    path: 'clubs/:id',
    title: 'Club details — CourtSide',
    loadComponent: () =>
      import('./features/clubs/club-detail/club-detail.component').then((m) => m.ClubDetailComponent),
  },
  {
    path: 'clubs/:clubId/dashboard',
    title: 'Dashboard — CourtSide',
    canActivate: [staffGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'clubs/:courtId/availability/:date',
    title: 'Availability — CourtSide',
    loadComponent: () =>
      import('./features/bookings/availability/availability.component').then((m) => m.AvailabilityComponent),
  },
  {
    path: 'login',
    title: 'Sign in — CourtSide',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    title: 'Create account — CourtSide',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'my-bookings',
    title: 'My bookings — CourtSide',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/bookings/my-bookings/my-bookings.component').then((m) => m.MyBookingsComponent),
  },
  {
    path: 'my-clubs',
    title: 'My clubs — CourtSide',
    canActivate: [staffGuard],
    loadComponent: () =>
      import('./features/clubs/my-clubs/my-clubs.component').then((m) => m.MyClubsComponent),
  },
  {
    path: 'waitlist',
    title: 'My waitlist — CourtSide',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/bookings/waitlist/waitlist.component').then((m) => m.WaitlistComponent),
  },
  {
    path: 'profile',
    title: 'My profile — CourtSide',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/profile/profile.component').then((m) => m.ProfileComponent),
  },
  {
    // Unknown URL: send people somewhere useful instead of a blank screen.
    path: '**',
    redirectTo: '',
  },
];
