import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  // protected: the template may use it, other classes may not.
  protected readonly auth = inject(AuthService);

  ngOnInit(): void {
    // A page reload keeps the token in localStorage but loses the in-memory profile,
    // so the navbar would not know the role. Re-fetch it once at startup; if the
    // token has expired the interceptor refreshes it transparently, and if that
    // fails too the session is cleared.
    if (this.auth.isLoggedIn()) {
      this.auth.loadCurrentUser().subscribe({ error: () => this.auth.clearSession() });
    }
  }
}
