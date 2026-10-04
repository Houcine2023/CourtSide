import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom, map } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { ClubService } from '../../../core/services/club.service';
import { ToastService } from '../../../core/services/toast.service';
import { Club, Court, CourtRequest, OpeningHours, OpeningHoursRequest } from '../../../core/models/api.models';
import { CourtListComponent } from '../court-list/court-list.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

@Component({
  selector: 'app-club-detail',
  imports: [RouterLink, CourtListComponent, FormsModule, ModalComponent],
  templateUrl: './club-detail.component.html',
  styleUrl: './club-detail.component.scss',
})
export class ClubDetailComponent {
  private readonly clubService = inject(ClubService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  /**
   * Page-level load state. Deliberately NOT reused by mutations: a mutation
   * that flips this to true blanks the whole page behind the dialog and the
   * error branch then replaces the page with "Failed to load club details."
   */
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly isStaff = computed(() => this.auth.isStaff());

  /**
   * Driven by the route param stream (not a snapshot) so navigating between
   * clubs actually re-triggers the effect below and refetches for the new id.
   */
  private readonly routeId = toSignal(
    this.route.paramMap.pipe(map((params) => Number(params.get('id')))),
    { initialValue: NaN },
  );

  protected readonly clubId = computed(() => (Number.isFinite(this.routeId()) ? this.routeId() : null));

  protected readonly club = signal<Club | null>(null);
  protected readonly openingHours = signal<OpeningHours[]>([]);

  /**
   * Bumped by every successful mutation. Child components take this as an
   * input and refetch when it changes, so lists stay in step with the server
   * without the page needing to reload.
   */
  protected readonly revision = signal(0);

  /**
   * Re-runs on navigation to a different club id, and bumps `revision` so the
   * court list refetches for the new club too.
   */
  constructor() {
    effect(() => {
      this.clubId();
      untracked(() => void this.reload());
    });
  }

  /** Full refetch of the club and its opening hours. */
  protected async reload(): Promise<void> {
    const id = this.clubId();
    if (!id) return;

    this.loading.set(true);
    this.error.set(null);
    try {
      const [club, hours] = await Promise.all([
        firstValueFrom(this.clubService.getClub(id)),
        firstValueFrom(this.clubService.getOpeningHours(id)),
      ]);
      this.club.set(club);
      this.openingHours.set(hours);
    } catch (err: any) {
      this.error.set(err?.error?.message || 'Failed to load club details.');
    } finally {
      this.loading.set(false);
    }
  }

  // Court management state
  protected readonly showCourtDialog = signal(false);
  protected readonly editingCourt = signal<Court | null>(null);
  protected readonly saving = signal(false);
  // Use regular object for template-driven form
  protected courtForm: CourtRequest = { name: '', sport: 'PADEL', slotMinutes: 90, pricePerSlot: 0, active: true };

  // Opening hours management state
  protected readonly showHoursDialog = signal(false);
  protected readonly editingDay = signal<number | null>(null);
  // Use regular object for template-driven form
  protected hoursForm: OpeningHoursRequest = { dayOfWeek: 1, opens: '08:00:00', closes: '22:00:00' };

  protected readonly dayNames = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  /** Numeric day values for the select; index 0 is Monday. */
  protected readonly dayOptions = this.dayNames.map((name, index) => ({
    value: index + 1,
    label: name,
  }));

  protected formatTime(time: string): string {
    return time.substring(0, 5);
  }

  protected getDayName(dayOfWeek: number): string {
    return this.dayNames[dayOfWeek - 1] ?? '';
  }

  protected openCourtDialog(court?: Court): void {
    if (court) {
      this.editingCourt.set(court);
      this.courtForm = {
        name: court.name,
        sport: court.sport,
        slotMinutes: court.slotMinutes,
        pricePerSlot: court.pricePerSlot,
        active: court.active,
      };
    } else {
      this.editingCourt.set(null);
      this.courtForm = { name: '', sport: 'PADEL', slotMinutes: 90, pricePerSlot: 0, active: true };
    }
    this.showCourtDialog.set(true);
  }

  protected closeCourtDialog(): void {
    this.showCourtDialog.set(false);
    this.editingCourt.set(null);
  }

  protected async saveCourt(): Promise<void> {
    const id = this.clubId();
    if (!id || this.saving()) return;

    this.saving.set(true);
    try {
      const editing = this.editingCourt();
      if (editing) {
        await firstValueFrom(this.clubService.updateCourt(editing.id, this.courtForm));
        this.toast.success('Court updated.');
      } else {
        await firstValueFrom(this.clubService.createCourt(id, this.courtForm));
        this.toast.success('Court created.');
      }
      this.closeCourtDialog();
      this.revision.update((n) => n + 1);
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to save court.');
    } finally {
      this.saving.set(false);
    }
  }

  protected async deleteCourt(courtId: number): Promise<void> {
    if (!confirm('Deactivate this court? It will no longer be bookable.')) return;

    try {
      await firstValueFrom(this.clubService.deactivateCourt(courtId));
      this.toast.success('Court deactivated.');
      this.revision.update((n) => n + 1);
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to deactivate court.');
    }
  }

  protected openHoursDialog(dayOfWeek?: number, existing?: OpeningHours): void {
    if (existing) {
      this.editingDay.set(existing.dayOfWeek);
      this.hoursForm = { dayOfWeek: existing.dayOfWeek, opens: existing.opens, closes: existing.closes };
    } else {
      this.editingDay.set(dayOfWeek ?? 1);
      this.hoursForm = { dayOfWeek: dayOfWeek ?? 1, opens: '08:00:00', closes: '22:00:00' };
    }
    this.showHoursDialog.set(true);
  }

  protected closeHoursDialog(): void {
    this.showHoursDialog.set(false);
    this.editingDay.set(null);
  }

  protected async saveHours(): Promise<void> {
    const id = this.clubId();
    if (!id || this.saving()) return;

    this.saving.set(true);
    try {
      await firstValueFrom(this.clubService.setOpeningHours(id, this.hoursForm));
      this.toast.success('Opening hours saved.');
      this.closeHoursDialog();
      // Refetch: an upsert can change another day's row (moving hours to a
      // different weekday), so the list must come from the server, not guesswork.
      await this.reload();
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to save opening hours.');
    } finally {
      this.saving.set(false);
    }
  }

  protected async deleteHours(dayOfWeek: number): Promise<void> {
    if (!confirm(`Remove opening hours for ${this.getDayName(dayOfWeek)}?`)) return;

    const id = this.clubId();
    if (!id) return;

    try {
      await firstValueFrom(this.clubService.deleteOpeningHours(id, dayOfWeek));
      this.toast.success(`Opening hours removed for ${this.getDayName(dayOfWeek)}.`);
      // The removed row only disappears once the server state is refetched.
      await this.reload();
    } catch (err: any) {
      this.toast.error(err?.error?.message || 'Failed to delete opening hours.');
    }
  }

  protected getExistingHours(dayOfWeek: number): OpeningHours | undefined {
    return this.openingHours().find((h) => h.dayOfWeek === dayOfWeek);
  }
}
