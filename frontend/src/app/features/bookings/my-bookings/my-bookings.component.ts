import { Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';

import { AuthService } from '../../../core/services/auth.service';
import { BookingService } from '../../../core/services/booking.service';
import { Booking, Page } from '../../../core/models/api.models';

@Component({
  selector: 'app-my-bookings',
  imports: [],
  templateUrl: './my-bookings.component.html',
  styleUrl: './my-bookings.component.scss',
})
export class MyBookingsComponent {
  private readonly auth = inject(AuthService);
  private readonly bookingService = inject(BookingService);

  protected readonly upcomingOnly = signal(false);
  protected readonly page = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly bookingsData = signal<Page<Booking> | null>(null);

  protected readonly bookings = computed(() => this.bookingsData());

  protected readonly statusLabels: Record<string, string> = {
    HOLD: 'On Hold',
    CONFIRMED: 'Confirmed',
    CANCELLED: 'Cancelled',
    NO_SHOW: 'No Show',
  };

  protected readonly statusClasses: Record<string, string> = {
    HOLD: 'status-hold',
    CONFIRMED: 'status-confirmed',
    CANCELLED: 'status-cancelled',
    NO_SHOW: 'status-no-show',
  };

  constructor() {
    this.loadBookings();
  }

  protected loadBookings(): void {
    this.loading.set(true);
    this.error.set(null);
    this.bookingService.myBookings(this.upcomingOnly(), this.page()).pipe(
      tap(() => {}),
      catchError(() => {
        this.error.set('Failed to load bookings.');
        return [];
      }),
    ).subscribe((page) => {
      this.bookingsData.set(page);
      this.loading.set(false);
    });
  }

  protected toggleUpcoming(): void {
    this.upcomingOnly.update((v) => !v);
    this.page.set(0);
    this.loadBookings();
  }

  protected nextPage(): void {
    this.page.update((p) => p + 1);
    this.loadBookings();
  }

  protected prevPage(): void {
    this.page.update((p) => Math.max(0, p - 1));
    this.loadBookings();
  }

  protected formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  protected getStatusLabel(status: string): string {
    return this.statusLabels[status] ?? status;
  }

  protected getStatusClass(status: string): string {
    return this.statusClasses[status] ?? '';
  }

  protected async confirmBooking(id: number): Promise<void> {
    try {
      await firstValueFrom(this.bookingService.confirm(id));
      this.error.set(null);
      this.loadBookings();
    } catch {
      this.error.set('Failed to confirm booking.');
    }
  }

  protected async cancelBooking(id: number): Promise<void> {
    if (!confirm('Are you sure you want to cancel this booking?')) {
      return;
    }
    try {
      await firstValueFrom(this.bookingService.cancel(id));
      this.error.set(null);
      this.loadBookings();
    } catch {
      this.error.set('Failed to cancel booking.');
    }
  }
}