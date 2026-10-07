import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from '../services/auth.service';

/**
 * Route guards.
 *
 * Reminder that matters: these are UX, not security. Anyone can edit the JavaScript
 * in their browser and reach a "protected" route — they simply find an empty page,
 * because every API call behind it is refused by the backend. The guard exists to
 * avoid showing a broken screen, never to protect data.
 */

/** Requires any authenticated user. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    return true;
  }
  // Remember where they were going, so login can send them back there.
  return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
};

/** Requires MANAGER or ADMIN — the club-management area. */
export const staffGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
  }

  const decide = () => (auth.isStaff() ? true : router.createUrlTree(['/clubs']));

  // The token survives a reload; the in-memory profile does not, and this guard
  // runs before the root component has finished fetching it. Reading a profile
  // that is still null therefore said "not staff" and bounced managers off their
  // own page on every hard refresh — so on a cold session, wait for the answer
  // rather than guess it. Logged-in but not staff is sent home rather than to a
  // login page they have already passed.
  if (auth.role() !== null) {
    return decide();
  }

  return auth.loadCurrentUser().pipe(
    map(decide),
    // The session turned out to be unusable. Send them to login with the URL
    // they wanted so signing in again returns them to it.
    catchError(() => of(router.createUrlTree(['/login'], { queryParams: { redirect: state.url } }))),
  );
};
