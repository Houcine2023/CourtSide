import { Component, VERSION, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './core/services/auth.service';
import { ClubService } from './core/services/club.service';
import { ToastContainerComponent } from './core/services/toast.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastContainerComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly clubService = inject(ClubService);
  protected readonly menuOpen = signal(false);
  /** Read from the framework itself rather than hardcoded, so it cannot go stale. */
  protected readonly angularVersion = VERSION.major;

  private readonly managedClubs = signal<number[]>([]);

  /**
   * The dashboard is scoped to a single club, so the navbar resolves it from the
   * clubs this user actually manages. It previously linked to /clubs/1/dashboard,
   * which sent every manager to whichever club happened to hold id 1 — usually
   * one they did not manage, which the backend rejects with 403.
   */
  protected readonly dashboardLink = computed(() => {
    const id = this.managedClubs()[0];
    return id ? ['/clubs', id, 'dashboard'] : null;
  });

  constructor() {
    // A reload keeps the token but loses the in-memory profile, so the navbar would
    // not know the role. Re-fetch it once; the interceptor silently refreshes an
    // expired token, and if even that fails the session is cleared.
    if (this.auth.isLoggedIn()) {
      this.auth.loadCurrentUser().subscribe({ error: () => this.auth.clearSession() });
    }

    effect(() => {
      const user = this.auth.currentUser();
      untracked(() => void this.resolveManagedClubs(user?.role === 'ADMIN' || user?.role === 'MANAGER'));
    });
  }

  private async resolveManagedClubs(shouldLoad: boolean): Promise<void> {
    if (!shouldLoad) {
      this.managedClubs.set([]);
      return;
    }

    const me = this.auth.currentUser();
    try {
      const page = await firstValueFrom(this.clubService.search('', '', 0, 200));
      const role = this.auth.currentUser()?.role;
      const mine =
        role === 'ADMIN' ? page.content : page.content.filter((club) => club.managerId === me?.id);
      this.managedClubs.set(mine.map((club) => club.id));
    } catch {
      // A failed lookup just means the Dashboard link stays hidden, which is
      // better than linking somewhere the user cannot open.
      this.managedClubs.set([]);
    }
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }
}
