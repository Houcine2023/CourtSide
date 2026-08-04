import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-card">
      <h1>Welcome back</h1>
      <p class="muted">Sign in to book a court.</p>

      <form [formGroup]="form" (ngSubmit)="submit()">
        <label for="email">Email</label>
        <input id="email" type="email" formControlName="email" autocomplete="email" />
        @if (showError('email')) {
          <small class="error">A valid email is required.</small>
        }

        <label for="password">Password</label>
        <input id="password" type="password" formControlName="password" autocomplete="current-password" />
        @if (showError('password')) {
          <small class="error">Your password is required.</small>
        }

        @if (serverError()) {
          <div class="alert">{{ serverError() }}</div>
        }

        <!-- Disabled while in flight: the cheapest defence against double submission. -->
        <button type="submit" class="btn primary" [disabled]="loading()">
          {{ loading() ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>

      <p class="muted">No account yet? <a routerLink="/register">Create one</a></p>
    </div>
  `,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly serverError = signal<string | null>(null);

  /**
   * Reactive form: validation rules live in TypeScript where they can be read and
   * tested, not scattered across template attributes.
   */
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  /** Only complain once the user has actually interacted with the field. */
  showError(field: 'email' | 'password'): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.dirty || control.touched);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.serverError.set(null);

    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          // Load the profile before navigating: guards and the navbar need the role.
          this.auth.loadCurrentUser().subscribe({
            next: () => this.redirect(),
            error: () => this.redirect(),
          });
        },
        // The backend deliberately returns the same 401 whether the email or the
        // password was wrong, so the UI must not invent a more specific message.
        error: () => this.serverError.set('Invalid email or password.'),
      });
  }

  private redirect(): void {
    const target = this.route.snapshot.queryParamMap.get('redirect') ?? '/clubs';
    this.router.navigateByUrl(target);
  }
}
