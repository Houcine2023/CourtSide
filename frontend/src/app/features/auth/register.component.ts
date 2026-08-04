import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-card">
      <h1>Create your account</h1>
      <p class="muted">Book padel and tennis courts in seconds.</p>

      <form [formGroup]="form" (ngSubmit)="submit()">
        <label for="fullName">Full name</label>
        <input id="fullName" type="text" formControlName="fullName" autocomplete="name" />
        @if (showError('fullName')) {
          <small class="error">Your name is required.</small>
        }

        <label for="email">Email</label>
        <input id="email" type="email" formControlName="email" autocomplete="email" />
        @if (showError('email')) {
          <small class="error">A valid email is required.</small>
        }

        <label for="password">Password</label>
        <input id="password" type="password" formControlName="password" autocomplete="new-password" />
        <!-- The same minimum the backend enforces: the UI mirrors the rule, the
             server owns it. Client validation is convenience, never protection. -->
        @if (showError('password')) {
          <small class="error">At least 8 characters.</small>
        }

        @if (serverError()) {
          <div class="alert">{{ serverError() }}</div>
        }

        <button type="submit" class="btn primary" [disabled]="loading()">
          {{ loading() ? 'Creating…' : 'Create account' }}
        </button>
      </form>

      <p class="muted">Already registered? <a routerLink="/login">Sign in</a></p>
    </div>
  `,
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly serverError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  showError(field: 'fullName' | 'email' | 'password'): boolean {
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
      .register(this.form.getRawValue())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () =>
          this.auth.loadCurrentUser().subscribe({
            next: () => this.router.navigate(['/clubs']),
            error: () => this.router.navigate(['/clubs']),
          }),
        error: (error: HttpErrorResponse) => {
          // 409 is the one case worth naming precisely: it is not a credential
          // secret, and "try another email" is the only useful next step.
          this.serverError.set(
            error.status === 409
              ? 'An account already exists with this email.'
              : 'Registration failed. Please check your details and try again.',
          );
        },
      });
  }
}
