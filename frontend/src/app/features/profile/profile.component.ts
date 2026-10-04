import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { Me } from '../../core/models/api.models';
import { ModalComponent } from '../../shared/modal/modal.component';

@Component({
  selector: 'app-profile',
  imports: [FormsModule, ModalComponent],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent {
  protected readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly profile = signal<Me | null>(null);

  protected readonly showPasswordDialog = signal(false);
  protected readonly changingPassword = signal(false);
  /** Named passwordFields so the template's #passwordForm ref cannot shadow it. */
  protected passwordFields = { currentPassword: '', newPassword: '', confirmPassword: '' };

  protected formData = { fullName: '', email: '' };

  constructor() {
    this.loadProfile();
  }

  private async loadProfile(): Promise<void> {
    if (!this.auth.isLoggedIn()) return;
    try {
      const me = await firstValueFrom(this.auth.loadCurrentUser());
      this.profile.set(me);
      this.formData = { fullName: me.fullName, email: me.email };
    } catch {
      this.toast.error('Failed to load profile.');
    }
  }

  protected async updateProfile(): Promise<void> {
    const fullName = this.formData.fullName.trim();
    if (!fullName || this.loading()) return;

    this.loading.set(true);
    try {
      const updated = await firstValueFrom(this.auth.updateProfile({ fullName }));
      this.profile.set(updated);
      this.formData.fullName = updated.fullName;
      this.toast.success('Profile updated.');
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to update profile.');
    } finally {
      this.loading.set(false);
    }
  }

  protected openPasswordDialog(): void {
    this.passwordFields = { currentPassword: '', newPassword: '', confirmPassword: '' };
    this.showPasswordDialog.set(true);
  }

  protected closePasswordDialog(): void {
    this.showPasswordDialog.set(false);
  }

  protected async changePassword(): Promise<void> {
    if (this.changingPassword()) return;

    if (this.passwordFields.newPassword !== this.passwordFields.confirmPassword) {
      this.toast.error('New passwords do not match.');
      return;
    }
    if (this.passwordFields.newPassword.length < 8) {
      this.toast.error('Password must be at least 8 characters.');
      return;
    }

    this.changingPassword.set(true);
    try {
      await firstValueFrom(
        this.auth.changePassword({
          currentPassword: this.passwordFields.currentPassword,
          newPassword: this.passwordFields.newPassword,
        }),
      );
      this.showPasswordDialog.set(false);
      this.toast.success('Password changed. Please sign in again.');
      // Every refresh token was revoked server-side, so the old session is dead.
      await this.router.navigate(['/login']);
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to change password.');
    } finally {
      this.changingPassword.set(false);
    }
  }

  protected formatRole(role: string): string {
    return role.charAt(0) + role.slice(1).toLowerCase();
  }

  protected signOut(): void {
    this.auth.logout();
  }
}
