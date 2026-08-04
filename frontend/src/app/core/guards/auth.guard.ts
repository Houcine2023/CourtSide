import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

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
  // Logged in but not staff: send them home rather than to a login page they have
  // already passed — a login form would be a confusing answer to "not allowed".
  return auth.isStaff() ? true : router.createUrlTree(['/clubs']);
};
