import { Component, inject, signal } from '@angular/core';
import { email, form, minLength, required, submit } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';

/**
 * Sign-in page, built with SIGNAL FORMS (@angular/forms/signals, stable in v22).
 *
 * The model is a plain signal; `form()` wraps it with validation and state. Compared
 * with reactive forms there is no FormGroup/FormControl indirection: the value IS the
 * signal, fully typed, and field state (touched / invalid / errors) is read straight
 * from the field in the template.
 */
@Component({
  selector: 'app-login',
  imports: [RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly serverError = signal<string | null>(null);

  /** The data. A signal, not a form object — the form is derived from it. */
  private readonly credentials = signal({ email: '', password: '' });

  /** Validation is declared on paths, so it is type-checked against the model. */
  protected readonly loginForm = form(this.credentials, (path) => {
    required(path.email, { message: 'Your email is required.' });
    email(path.email, { message: 'Enter a valid email address.' });
    required(path.password, { message: 'Your password is required.' });
    minLength(path.password, 8, { message: 'At least 8 characters.' });
  });

  protected async onSubmit(): Promise<void> {
    this.serverError.set(null);

    // submit() marks everything touched, blocks while the action runs (which drives
    // the button's disabled state), and only calls the action when the form is valid.
    await submit(this.loginForm, async () => {
      try {
        await firstValueFrom(this.auth.login(this.credentials()));
        // Load the profile before navigating: the navbar and guards need the role.
        await firstValueFrom(this.auth.loadCurrentUser()).catch(() => undefined);
        await this.router.navigateByUrl(this.redirectTarget());
      } catch {
        // The API deliberately answers the same 401 whether the email or the password
        // was wrong, so the UI must not invent a more specific message.
        this.serverError.set('Invalid email or password.');
      }
      return undefined;
    });
  }

  private redirectTarget(): string {
    return this.route.snapshot.queryParamMap.get('redirect') ?? '/clubs';
  }
}
