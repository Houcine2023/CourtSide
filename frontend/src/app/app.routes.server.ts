import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    // The landing page is entirely driven by live club/court data, so prerendering
    // it would mean hitting the API during `ng build`. Besides being slow, that
    // makes the build fail whenever the backend happens to be down. Client-side
    // rendering also renders the freshly-fetched data immediately, instead of
    // flashing a prerendered skeleton that is already stale.
    path: '',
    renderMode: RenderMode.Client,
  },

  /*
   * Every route behind a guard is rendered on the client, and that is a
   * correctness requirement rather than a performance choice.
   *
   * On the server there is no localStorage, so AuthService.isLoggedIn() is always
   * false there. A guard that runs during SSR therefore always answers "not
   * authenticated" and redirects to /login - which means reloading /profile, or
   * opening a guarded link in a new tab, threw a perfectly valid session out to
   * the login form. Hydration restored the token afterwards, but the router had
   * already navigated away.
   *
   * Rendering these in the browser means the guard sees the real token and makes
   * the right decision. The pages behind them were never cacheable anyway,
   * since their content is per-user.
   */
  {
    path: 'clubs/:clubId/dashboard',
    renderMode: RenderMode.Client,
  },
  {
    path: 'my-bookings',
    renderMode: RenderMode.Client,
  },
  {
    path: 'my-clubs',
    renderMode: RenderMode.Client,
  },
  {
    path: 'waitlist',
    renderMode: RenderMode.Client,
  },
  {
    path: 'profile',
    renderMode: RenderMode.Client,
  },

  // Unguarded and identical for everyone, so these are safe to render on the
  // server and are the pages that actually benefit from it.
  {
    path: 'clubs/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'clubs/:courtId/availability/:date',
    renderMode: RenderMode.Server,
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];
