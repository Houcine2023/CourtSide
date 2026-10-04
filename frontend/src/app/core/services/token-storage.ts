import { PLATFORM_ID, Service, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * The only place that touches `localStorage`.
 *
 * WHY THIS EXISTS: with SSR the application first runs in Node, where `localStorage`
 * simply does not exist — touching it throws and the whole page fails to render.
 * Isolating browser-only APIs behind one service means exactly one class needs the
 * platform check, instead of every consumer remembering to guard.
 *
 * On the server every read returns null, so the rendered HTML is the logged-out view;
 * hydration in the browser then reads the real tokens and updates the UI.
 */
@Service()
export class TokenStorage {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  read(key: string): string | null {
    return this.isBrowser ? localStorage.getItem(key) : null;
  }

  write(key: string, value: string): void {
    if (this.isBrowser) {
      localStorage.setItem(key, value);
    }
  }

  remove(key: string): void {
    if (this.isBrowser) {
      localStorage.removeItem(key);
    }
  }
}
