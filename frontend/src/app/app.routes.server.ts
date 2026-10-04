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
  {
    path: 'clubs/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'clubs/:clubId/dashboard',
    renderMode: RenderMode.Server,
  },
  {
    path: 'clubs/:courtId/availability/:date',
    renderMode: RenderMode.Server,
  },
  {
    path: 'my-clubs',
    renderMode: RenderMode.Server,
  },
  {
    path: 'waitlist',
    renderMode: RenderMode.Server,
  },
  {
    path: 'profile',
    renderMode: RenderMode.Server,
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];
