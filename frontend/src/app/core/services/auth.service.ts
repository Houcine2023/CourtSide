import { HttpClient } from '@angular/common/http';
import { Service, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthResponse, ChangePasswordRequest, LoginRequest, Me, RegisterRequest, Role, UpdateProfileRequest } from '../models/api.models';
import { TokenStorage } from './token-storage';

const ACCESS_TOKEN_KEY = 'courtside.accessToken';
const REFRESH_TOKEN_KEY = 'courtside.refreshToken';

/**
 * Authentication state and the token pair.
 *
 * State lives in SIGNALS: readable synchronously in a guard, and templates that read
 * them re-render on their own — no subscriptions, no manual change detection.
 * The writable signals stay private and are exposed read-only, so no component can
 * corrupt session state by accident.
 *
 * Storage: tokens sit in localStorage, readable by any script on the page. That is the
 * accepted XSS trade-off of a token-based SPA; it is mitigated by a 15-minute access
 * token and server-side refresh rotation, so a stolen pair is short-lived and its
 * reuse is detected. httpOnly cookies would swap this for a CSRF problem instead.
 */
@Service()
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly storage = inject(TokenStorage);
  private readonly api = environment.apiUrl;

  private readonly accessTokenSignal = signal<string | null>(this.storage.read(ACCESS_TOKEN_KEY));
  private readonly currentUserSignal = signal<Me | null>(null);

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.accessTokenSignal() !== null);
  readonly role = computed<Role | null>(() => this.currentUserSignal()?.role ?? null);
  readonly isStaff = computed(() => this.role() === 'MANAGER' || this.role() === 'ADMIN');
  /** First name only — friendlier in the navbar than the full name. */
  readonly displayName = computed(() => this.currentUserSignal()?.fullName.split(' ')[0] ?? '');

  get accessToken(): string | null {
    return this.accessTokenSignal();
  }

  get refreshToken(): string | null {
    return this.storage.read(REFRESH_TOKEN_KEY);
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.api}/auth/register`, request)
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.api}/auth/login`, request)
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  /** Exchanges the refresh token for a new pair. Called by the interceptor on 401. */
  refresh(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.api}/auth/refresh`, { refreshToken: this.refreshToken })
      .pipe(tap((tokens) => this.storeTokens(tokens)));
  }

  /** Loads the profile behind the current token; the navbar and guards need the role. */
  loadCurrentUser(): Observable<Me> {
    return this.http.get<Me>(`${this.api}/me`).pipe(tap((me) => this.currentUserSignal.set(me)));
  }

  /** Persists a profile edit and refreshes the cached user so the navbar updates. */
  updateProfile(request: UpdateProfileRequest): Observable<Me> {
    return this.http
      .put<Me>(`${this.api}/me`, request)
      .pipe(tap((me) => this.currentUserSignal.set(me)));
  }

  /**
   * The server revokes every refresh token on a password change, so the tokens
   * held here are dead. Clear the session first and let the caller navigate —
   * otherwise the next API call fails with a confusing 401.
   */
  changePassword(request: ChangePasswordRequest): Observable<unknown> {
    return this.http.post(`${this.api}/me/password`, request).pipe(
      tap(() => this.clearSession()),
    );
  }

  logout(): void {
    const token = this.refreshToken;
    if (token) {
      // Ask the server to revoke it, but clear locally whatever happens: a failed
      // network call must never leave someone stuck in a "logged in" shell.
      this.http.post(`${this.api}/auth/logout`, { refreshToken: token }).subscribe({
        error: () => undefined,
      });
    }
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  /** Wipes local state without calling the server (used when a refresh fails). */
  clearSession(): void {
    this.storage.remove(ACCESS_TOKEN_KEY);
    this.storage.remove(REFRESH_TOKEN_KEY);
    this.accessTokenSignal.set(null);
    this.currentUserSignal.set(null);
  }

  private storeTokens(tokens: AuthResponse): void {
    this.storage.write(ACCESS_TOKEN_KEY, tokens.accessToken);
    this.storage.write(REFRESH_TOKEN_KEY, tokens.refreshToken);
    this.accessTokenSignal.set(tokens.accessToken);
  }
}
