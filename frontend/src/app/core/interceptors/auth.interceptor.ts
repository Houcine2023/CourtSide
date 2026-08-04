import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

/**
 * Shared refresh state.
 *
 * These live at module scope, not inside the function, because the interceptor
 * function runs once PER REQUEST: instance state would not be shared, and ten
 * requests expiring together would fire ten refresh calls. Nine of them would present
 * an already-used refresh token, and the backend's reuse detection would — correctly —
 * log the user out entirely. Sharing the in-flight refresh is what prevents that.
 */
let refreshInProgress = false;
const refreshedToken$ = new BehaviorSubject<string | null>(null);

/**
 * Attaches the access token, and transparently renews it once when it expires.
 *
 * Functional interceptor (Angular 15+) rather than a class: it composes with `inject()`
 * and needs no DI boilerplate.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  // Auth endpoints must not carry (or refresh) a token: sending an expired access
  // token to /auth/refresh would trigger an infinite refresh loop.
  const isAuthEndpoint = req.url.includes('/auth/');
  const isOurApi = req.url.startsWith(environment.apiUrl);

  const token = auth.accessToken;
  const authorized = token && isOurApi && !isAuthEndpoint ? withToken(req, token) : req;

  return next(authorized).pipe(
    catchError((error: unknown) => {
      const is401 = error instanceof HttpErrorResponse && error.status === 401;

      if (!is401 || isAuthEndpoint || !isOurApi || !auth.refreshToken) {
        return throwError(() => error);
      }
      return handleExpiredToken(req, next, auth);
    }),
  );
};

function withToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  // Requests are immutable — clone to modify. Mutating would break retries and
  // any other interceptor holding the original.
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

function handleExpiredToken(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  auth: AuthService,
): Observable<HttpEvent<unknown>> {
  // First 401 wins the right to refresh; everyone else queues on the result.
  if (!refreshInProgress) {
    refreshInProgress = true;
    refreshedToken$.next(null);

    return auth.refresh().pipe(
      switchMap((tokens) => {
        refreshInProgress = false;
        refreshedToken$.next(tokens.accessToken); // releases the queued requests
        return next(withToken(req, tokens.accessToken));
      }),
      catchError((refreshError: unknown) => {
        refreshInProgress = false;
        // The refresh token is dead (expired, revoked, or reuse was detected).
        // Nothing left to try: clear the session rather than loop.
        auth.clearSession();
        return throwError(() => refreshError);
      }),
    );
  }

  // A refresh is already running: wait for the new token, then replay this request.
  return refreshedToken$.pipe(
    filter((newToken): newToken is string => newToken !== null),
    take(1), // unsubscribe after the first value, or this never completes
    switchMap((newToken) => next(withToken(req, newToken))),
  );
}
