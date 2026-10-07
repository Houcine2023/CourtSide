import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { Club, ClubRequest } from '../../../core/models/api.models';
import { ClubService } from '../../../core/services/club.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { ClubPhotoComponent } from '../../../shared/club-photo/club-photo.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

@Component({
  selector: 'app-my-clubs',
  imports: [RouterLink, FormsModule, ClubPhotoComponent, ModalComponent],
  templateUrl: './my-clubs.component.html',
  styleUrl: './my-clubs.component.scss',
})
export class MyClubsComponent {
  private readonly clubService = inject(ClubService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  /** Which club's photo upload is in flight, so only that card's control disables. */
  protected readonly photoBusyId = signal<number | null>(null);
  protected readonly showCreateDialog = signal(false);
  protected readonly showEditDialog = signal(false);
  protected readonly editingClub = signal<Club | null>(null);

  protected readonly newClub = signal({ name: '', city: '', address: '' });
  protected readonly editForm = signal<ClubRequest>({ name: '', city: '', address: '' });

  /**
   * Plain signal, not a computed. The previous version called loadClubs() from
   * inside a computed, so every read of clubs() in the template fired another
   * HTTP request — and writing a signal while a computed is evaluating throws.
   */
  private readonly allClubs = signal<Club[]>([]);

  /**
   * Only clubs this user actually manages. Filtering client-side avoids adding
   * a new endpoint, and the backend still enforces ownership on every write.
   * Admins see all clubs, matching their global access.
   */
  protected readonly clubs = computed(() => {
    const user = this.auth.currentUser();
    if (!user) return [] as Club[];
    if (user.role === 'ADMIN') return this.allClubs();
    return this.allClubs().filter((club) => club.managerId === user.id);
  });

  protected readonly canManage = computed(() => {
    const role = this.auth.currentUser()?.role;
    return role === 'MANAGER' || role === 'ADMIN';
  });

  constructor() {
    effect(() => {
      // Refetch once the signed-in user resolves.
      this.auth.currentUser();
      untracked(() => void this.loadClubs());
    });
  }

  private async loadClubs(): Promise<void> {
    if (!this.canManage()) {
      this.allClubs.set([]);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await firstValueFrom(this.clubService.search('', '', 0, 200));
      this.allClubs.set(page.content);
    } catch (err: any) {
      this.error.set(err?.error?.message || 'Failed to load clubs.');
      this.allClubs.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  protected openCreateDialog(): void {
    this.newClub.set({ name: '', city: '', address: '' });
    this.showCreateDialog.set(true);
  }

  protected closeCreateDialog(): void {
    this.showCreateDialog.set(false);
  }

  protected openEditDialog(club: Club): void {
    this.editingClub.set(club);
    this.editForm.set({ name: club.name, city: club.city, address: club.address });
    this.showEditDialog.set(true);
  }

  protected closeEditDialog(): void {
    this.showEditDialog.set(false);
    this.editingClub.set(null);
  }

  protected async createClub(): Promise<void> {
    const data = this.newClub();
    if (this.saving()) return;

    this.saving.set(true);
    try {
      await firstValueFrom(this.clubService.create(data));
      this.toast.success('Club created.');
      this.closeCreateDialog();
      await this.loadClubs();
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to create club.');
    } finally {
      this.saving.set(false);
    }
  }

  protected async updateClub(): Promise<void> {
    const club = this.editingClub();
    if (!club || this.saving()) return;

    const data = this.editForm();
    this.saving.set(true);
    try {
      await firstValueFrom(this.clubService.update(club.id, data));
      this.toast.success('Club updated.');
      this.closeEditDialog();
      await this.loadClubs();
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to update club.');
    } finally {
      this.saving.set(false);
    }
  }

  protected async deleteClub(clubId: number): Promise<void> {
    if (!confirm('Delete this club? This action cannot be undone.')) return;

    try {
      await firstValueFrom(this.clubService.delete(clubId));
      this.toast.success('Club deleted.');
      await this.loadClubs();
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to delete club.');
    }
  }

  /**
   * Cache-buster per club, bumped after every upload and removal.
   *
   * photoUrl never changes — the API answers with the same address whether a
   * photo exists or not — so a card that already failed to load one would keep
   * the broken <img> forever, since the binding value it reads is unchanged.
   * A new query parameter is the simplest thing that makes the browser ask again.
   */
  private readonly photoVersions = signal<Record<number, number>>({});

  protected photoUrl(club: Club): string {
    const version = this.photoVersions()[club.id];
    return version ? `${club.photoUrl}?v=${version}` : club.photoUrl;
  }

  protected async onPhotoSelected(club: Club, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    // Cleared first so picking the same file after a rejected attempt fires change again.
    input.value = '';
    if (!file) return;

    this.photoBusyId.set(club.id);
    try {
      await firstValueFrom(this.clubService.uploadPhoto(club.id, file));
      this.photoVersions.update((versions) => ({ ...versions, [club.id]: Date.now() }));
      this.toast.success('Club photo updated.');
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to upload photo.');
    } finally {
      this.photoBusyId.set(null);
    }
  }

  protected async removePhoto(clubId: number): Promise<void> {
    if (this.photoBusyId() !== null) return;

    this.photoBusyId.set(clubId);
    try {
      await firstValueFrom(this.clubService.deletePhoto(clubId));
      this.photoVersions.update((versions) => ({ ...versions, [clubId]: Date.now() }));
      this.toast.success('Club photo removed.');
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to remove photo.');
    } finally {
      this.photoBusyId.set(null);
    }
  }
}
