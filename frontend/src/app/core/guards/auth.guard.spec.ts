import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  GuardResult,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { firstValueFrom, isObservable, of, throwError } from 'rxjs';

import { authGuard, staffGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { Me } from '../models/api.models';

/**
 * Guards are UX rather than security — the backend refuses the API calls either
 * way — but a guard that guesses wrongly still throws a signed-in user out of a
 * page they are allowed to open.
 *
 * The case worth naming: the access token survives a page reload, the in-memory
 * profile does not, and the guard runs before the root component has refetched
 * it. Reading a profile that is still null said "not staff" and bounced every
 * manager off /my-clubs on a hard refresh, which the e2e suite caught.
 */

const route = {} as ActivatedRouteSnapshot;
const state = { url: '/my-clubs' } as RouterStateSnapshot;

function configure(stub: Partial<AuthService>) {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: AuthService, useValue: stub }],
  });
}

function run(guard: CanActivateFn): ReturnType<CanActivateFn> {
  return TestBed.runInInjectionContext(() => guard(route, state));
}

/** Awaits whichever form the guard chose to answer in. */
function settle(result: ReturnType<CanActivateFn>): Promise<GuardResult> {
  return isObservable(result) ? firstValueFrom(result) : Promise.resolve(result);
}

function resolve(guard: CanActivateFn): Promise<GuardResult> {
  return settle(run(guard));
}

/**
 * A session whose profile may or may not have arrived yet.
 *
 * The role starts as whatever is already in memory and is filled in by the
 * profile fetch, exactly like the real service — that transition is the point.
 */
function session(
  initialRole: Me['role'] | null,
  options: { loggedIn?: boolean; resolvesTo?: Me['role']; failLoad?: boolean } = {},
): Partial<AuthService> {
  let role = initialRole;
  return {
    isLoggedIn: () => options.loggedIn ?? true,
    role: () => role,
    isStaff: () => role === 'MANAGER' || role === 'ADMIN',
    loadCurrentUser: vi.fn(() => {
      if (options.failLoad) return throwError(() => new Error('401'));
      role = options.resolvesTo ?? role ?? 'MEMBER';
      return of({ role } as Me);
    }),
  } as unknown as Partial<AuthService>;
}

const signedOut = () => ({ isLoggedIn: () => false } as unknown as Partial<AuthService>);

describe('authGuard', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('sends anonymous visitors to the sign-in form, remembering where they were going', async () => {
    configure(signedOut());

    const result = (await resolve(authGuard)) as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/login');
    expect(decodeURIComponent(result.toString())).toContain('redirect=/my-clubs');
  });

  it('lets any signed-in user through', async () => {
    configure(session('MEMBER'));

    await expect(resolve(authGuard)).resolves.toBe(true);
  });
});

describe('staffGuard', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('sends anonymous visitors to the sign-in form', async () => {
    configure(signedOut());

    const result = (await resolve(staffGuard)) as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/login');
  });

  it('sends a signed-in member to the club directory rather than a login page', async () => {
    configure(session('MEMBER'));

    const result = (await resolve(staffGuard)) as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/clubs');
    expect(result.toString()).not.toContain('/login');
  });

  it('admits a manager whose profile is already in memory without a round trip', async () => {
    const stub = session('MANAGER');
    configure(stub);

    await expect(resolve(staffGuard)).resolves.toBe(true);
    expect(stub.loadCurrentUser).not.toHaveBeenCalled();
  });

  it('waits for the profile instead of guessing on a cold session', async () => {
    const stub = session(null, { resolvesTo: 'MANAGER' });
    configure(stub);

    const pending = run(staffGuard);
    // Not a boolean yet: the decision is deferred until the profile arrives.
    expect(pending).not.toBe(true);
    expect(stub.loadCurrentUser).toHaveBeenCalledTimes(1);

    await expect(settle(pending)).resolves.toBe(true);
  });

  it('still refuses the area once a cold session resolves to a member', async () => {
    configure(session(null));

    const result = (await resolve(staffGuard)) as UrlTree;
    expect(result.toString()).toContain('/clubs');
  });

  it('falls back to the sign-in form when the profile cannot be loaded at all', async () => {
    configure(session(null, { failLoad: true }));

    const result = (await resolve(staffGuard)) as UrlTree;
    expect(result.toString()).toContain('/login');
    expect(decodeURIComponent(result.toString())).toContain('redirect=/my-clubs');
  });
});
