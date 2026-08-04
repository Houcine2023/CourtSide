import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';

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
    redirectTo: 'clubs',
  },
  {
    path: 'clubs',
    title: 'Clubs — CourtSide',
    loadComponent: () =>
      import('./features/clubs/club-list.component').then((m) => m.ClubListComponent),
  },
  {
    path: 'login',
    title: 'Sign in — CourtSide',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    title: 'Create account — CourtSide',
    loadComponent: () =>
      import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'my-bookings',
    title: 'My bookings — CourtSide',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/bookings/my-bookings.component').then((m) => m.MyBookingsComponent),
  },
  {
    // Unknown URL: send people somewhere useful instead of a blank screen.
    path: '**',
    redirectTo: 'clubs',
  },
];
