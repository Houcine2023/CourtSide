import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { finalize } from 'rxjs';

import { Booking } from '../../core/models/api.models';
import { BookingService } from '../../core/services/booking.service';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [DatePipe],
  template: `
    <section class="page">
      <header class="page-head">
        <h1>My bookings</h1>
        <label class="toggle">
          <input type="checkbox" [checked]="upcomingOnly()" (change)="toggleUpcoming()" />
          Upcoming only
        </label>
      </header>

      @if (loading()) {
        <p class="muted">Loading…</p>
      } @else if (bookings().length === 0) {
        <p class="empty">No bookings yet.</p>
      } @else {
        <ul class="booking-list">
          @for (booking of bookings(); track booking.id) {
            <li class="card booking" [class.cancelled]="booking.status === 'CANCELLED'">
              <div>
                <h2>{{ booking.clubName }} — {{ booking.courtName }}</h2>
                <p class="muted">
                  {{ booking.start | date: 'EEE d MMM, HH:mm' }} –
                  {{ booking.end | date: 'HH:mm' }}
                </p>
                <span class="status status-{{ booking.status.toLowerCase() }}">
                  {{ booking.status }}
                </span>
              </div>
              <div class="booking-actions">
                <span class="price">{{ booking.price }} TND</span>
                @if (booking.status === 'CONFIRMED' || booking.status === 'HOLD') {
                  <button class="btn danger" (click)="cancel(booking)" [disabled]="busyId() === booking.id">
                    Cancel
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      }

      @if (message()) {
        <div class="alert">{{ message() }}</div>
      }
    </section>
  `,
})
export class MyBookingsComponent implements OnInit {
  private readonly bookingService = inject(BookingService);

  readonly bookings = signal<Booking[]>([]);
  readonly loading = signal(false);
  readonly upcomingOnly = signal(false);
  readonly busyId = signal<number | null>(null);
  readonly message = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  toggleUpcoming(): void {
    this.upcomingOnly.update((value) => !value);
    this.load();
  }

  cancel(booking: Booking): void {
    this.busyId.set(booking.id);
    this.message.set(null);

    this.bookingService
      .cancel(booking.id)
      .pipe(finalize(() => this.busyId.set(null)))
      .subscribe({
        // The server returns the UPDATED booking, so we patch that row instead of
        // refetching the list — one request instead of two, and no flicker.
        next: (updated) =>
          this.bookings.update((list) =>
            list.map((item) => (item.id === updated.id ? updated : item)),
          ),
        error: (error) =>
          // 422 means a business rule refused it — almost always the cancellation
          // window. Anything else is a genuine failure.
          this.message.set(
            error.status === 422
              ? 'Too late to cancel this booking (the free window has closed).'
              : 'Could not cancel this booking.',
          ),
      });
  }

  private load(): void {
    this.loading.set(true);
    this.bookingService
      .myBookings(this.upcomingOnly())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => this.bookings.set(page.content),
        error: () => this.message.set('Could not load your bookings.'),
      });
  }
}
