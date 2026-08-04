import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, Me, RegisterRequest, Role } from '../models/api.models';

const ACCESS_TOKEN_KEY = 'courtside.accessToken';
const REFRESH_TOKEN_KEY = 'courtside.refreshToken';

/**
 * Authentication state and the token pair.
 *
 * State lives in SIGNALS rather than BehaviorSubjects: a signal is synchronously
 * readable (`this.isLoggedIn()` in a guard, no subscription), and templates that read
 * it re-render automatically without an `async` pipe or manual change detection.
 *
 * Storage note: tokens go to localStorage, which is readable by any script on the
 * page — the accepted XSS trade-off of a token-based SPA. The mitigation is the short
 * 15-minute access-token lifetime plus refresh-token rotation on the server, so a
 * stolen pair is detectable and short-lived. httpOnly cookies would swap this for a
 * CSRF problem instead; neither is free.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = environment.apiUrl;

  /** Private writable state, exposed read-only — nobody outside can corrupt it. */
  private readonly _accessToken = signal<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY));
  private readonly _currentUser = signal<Me | null>(null);

  readonly currentUser = this._currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this._accessToken() !== null);
  readonly role = computed<Role | null>(() => this._currentUser()?.role ?? null);
  readonly isStaff = computed(() => {
    const role = this.role();
    return role === 'MANAGER' || role === 'ADMIN';
  });

  get accessToken(): string | null {
    return this._accessToken();
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
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

  /** Loads the profile behind the current token (role, name) after a login or reload. */
  loadCurrentUser(): Observable<Me> {
    return this.http
      .get<Me>(`${this.api}/me`)
      .pipe(tap((me) => this._currentUser.set(me)));
  }

  logout(): void {
    const token = this.refreshToken;
    // Tell the server to revoke the refresh token, but clear locally no matter what:
    // a failed network call must never leave the user stuck "logged in".
    if (token) {
      this.http.post(`${this.api}/auth/logout`, { refreshToken: token }).subscribe({
        error: () => void 0,
      });
    }
    this.clearSession();
    this.router.navigate(['/login']);
  }

  /** Wipes local state without calling the server (used when refreshing fails). */
  clearSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this._accessToken.set(null);
    this._currentUser.set(null);
  }

  private storeTokens(tokens: AuthResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    this._accessToken.set(tokens.accessToken);
  }
}
