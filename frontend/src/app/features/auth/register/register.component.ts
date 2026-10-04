import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { email, form, minLength, required, submit } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  imports: [RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly serverError = signal<string | null>(null);

  private readonly account = signal({ fullName: '', email: '', password: '' });

  protected readonly registerForm = form(this.account, (path) => {
    required(path.fullName, { message: 'Your name is required.' });
    required(path.email, { message: 'Your email is required.' });
    email(path.email, { message: 'Enter a valid email address.' });
    required(path.password, { message: 'Choose a password.' });
    // Mirrors the backend's @Size(min = 8). The UI reflects the rule; the server
    // owns it — client validation is convenience, never protection.
    minLength(path.password, 8, { message: 'At least 8 characters.' });
  });

  protected async onSubmit(): Promise<void> {
    this.serverError.set(null);

    await submit(this.registerForm, async () => {
      try {
        await firstValueFrom(this.auth.register(this.account()));
        await firstValueFrom(this.auth.loadCurrentUser()).catch(() => undefined);
        await this.router.navigate(['/clubs']);
      } catch (error) {
        // 409 is the one case worth naming precisely: it leaks nothing sensitive and
        // "try another email" is the only useful next step.
        const conflict = error instanceof HttpErrorResponse && error.status === 409;
        this.serverError.set(
          conflict
            ? 'An account already exists with this email.'
            : 'Registration failed. Please check your details and try again.',
        );
      }
      return undefined;
    });
  }
}
