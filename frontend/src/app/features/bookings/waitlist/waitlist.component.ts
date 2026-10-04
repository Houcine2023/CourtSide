import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, of, catchError, tap, firstValueFrom } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { BookingService } from '../../../core/services/booking.service';
import { WaitlistRequest, WaitlistResponse, BookingRequest } from '../../../core/models/api.models';
import { ModalComponent } from '../../../shared/modal/modal.component';

@Component({
  selector: 'app-waitlist',
  imports: [RouterLink, FormsModule, ModalComponent],
  templateUrl: './waitlist.component.html',
  styleUrl: './waitlist.component.scss',
})
export class WaitlistComponent {
  private readonly auth = inject(AuthService);
  private readonly bookingService = inject(BookingService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showJoinDialog = signal(false);

  protected waitlistData = { courtId: 0, start: '', end: '' };

  protected readonly waitlist = toSignal(
    this.bookingService.myWaitlist().pipe(
      tap(() => { this.loading.set(true); this.error.set(null); }),
      map((list) => ({ list, failed: false })),
      catchError(() => of({ list: [] as WaitlistResponse[], failed: true })),
      map((result) => {
        this.loading.set(false);
        if (result.failed) {
          this.error.set('Failed to load waitlist.');
        }
        return result.list;
      }),
    ),
    { initialValue: [] as WaitlistResponse[] },
  );

  protected readonly activeWaitlist = computed(() =>
    this.waitlist().filter((w) => w.active),
  );

  protected readonly notifiedWaitlist = computed(() =>
    this.waitlist().filter((w) => !w.active && w.notifiedAt),
  );

  protected readonly pastWaitlist = computed(() =>
    this.waitlist().filter((w) => !w.active && !w.notifiedAt),
  );

  protected formatDateTime(iso: string): string {
    return new Date(iso).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  protected openJoinDialog(): void {
    this.waitlistData = { courtId: 0, start: '', end: '' };
    this.showJoinDialog.set(true);
  }

  protected closeJoinDialog(): void {
    this.showJoinDialog.set(false);
  }

  protected async joinWaitlist(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);

    try {
      await firstValueFrom(this.bookingService.joinWaitlist({
        courtId: this.waitlistData.courtId,
        start: this.waitlistData.start,
        end: this.waitlistData.end,
      }));
      this.closeJoinDialog();
      this.error.set(null);
    } catch (err: any) {
      if (err?.status === 422) {
        this.error.set(err.error?.message || 'You are already on the waitlist for this slot.');
      } else if (err?.status === 409) {
        this.error.set('This slot is no longer available for waitlist.');
      } else {
        this.error.set('Failed to join waitlist.');
      }
    } finally {
      this.loading.set(false);
    }
  }

  protected async leaveWaitlist(id: number): Promise<void> {
    if (!confirm('Leave this waitlist?')) return;

    this.loading.set(true);
    this.error.set(null);

    try {
      await firstValueFrom(this.bookingService.leaveWaitlist(id));
      this.error.set(null);
    } catch (err: any) {
      this.error.set('Failed to leave waitlist.');
    } finally {
      this.loading.set(false);
    }
  }
}