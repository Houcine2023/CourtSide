import { Component, computed, effect, inject, OnDestroy, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { map, of, catchError, tap, switchMap } from 'rxjs';

import { Availability, Slot, BookingRequest, AvailabilityEvent } from '../../../core/models/api.models';
import { ClubService } from '../../../core/services/club.service';
import { BookingService } from '../../../core/services/booking.service';
import { AuthService } from '../../../core/services/auth.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { ModalComponent } from '../../../shared/modal/modal.component';

@Component({
  selector: 'app-availability',
  imports: [RouterLink, ModalComponent],
  templateUrl: './availability.component.html',
  styleUrl: './availability.component.scss',
})
export class AvailabilityComponent implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly clubService = inject(ClubService);
  private readonly bookingService = inject(BookingService);
  private readonly auth = inject(AuthService);
  private readonly ws = inject(WebSocketService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly selectedSlot = signal<Slot | null>(null);
  protected readonly showBookingDialog = signal(false);
  protected readonly bookingMode = signal<'book' | 'hold'>('book');
  protected readonly bookingLoading = signal(false);

  private readonly courtId = toSignal(this.route.paramMap.pipe(
    map((params) => Number(params.get('courtId')))
  ));

  protected readonly availability = toSignal(
    this.route.paramMap.pipe(
      map((params) => ({
        courtId: Number(params.get('courtId')),
        date: params.get('date') ?? new Date().toISOString().split('T')[0],
      })),
      tap(() => { this.loading.set(true); this.error.set(null); }),
      switchMap(({ courtId, date }) => this.clubService.getAvailability(courtId, date).pipe(
        map((avail) => ({ avail, failed: false })),
        catchError(() => of({ avail: null as Availability | null, failed: true })),
      )),
      map((result) => {
        this.loading.set(false);
        if (result.failed) {
          this.error.set('Failed to load availability.');
        }
        return result.avail;
      }),
    ),
    { initialValue: null as Availability | null },
  );

  protected readonly courtName = computed(() => this.availability()?.courtName ?? '');
  protected readonly date = computed(() => this.availability()?.date ?? '');
  protected readonly clubOpen = computed(() => this.availability()?.clubOpen ?? false);
  protected readonly slots = signal<Slot[]>([]);

  /**
   * A slot is only genuinely bookable if the backend considers it free AND it has
   * not already started. The API reports availability from the club's opening
   * hours alone, so on today's date the morning slots come back as "available"
   * long after they have gone by. Rendering those as clickable invited the user
   * into a dialog that could only end in a 422.
   *
   * The clock is captured once at construction rather than per render: a computed
   * that reads new Date() would be impure, and re-evaluating it on every change
   * detection pass is both wasteful and non-deterministic in tests.
   */
  private readonly now = new Date();

  private isPast(slot: Slot): boolean {
    return new Date(slot.start).getTime() <= this.now.getTime();
  }

  protected readonly availableSlots = computed(() =>
    this.slots().filter((s) => s.available && !this.isPast(s)),
  );

  protected readonly unavailableSlots = computed(() =>
    this.slots().filter((s) => !s.available || this.isPast(s)),
  );

  protected isSlotPast(slot: Slot): boolean {
    return this.isPast(slot);
  }

  private unsubscribeWs: (() => void) | null = null;

  // Update slots when availability changes & subscribe to WebSocket
  private readonly _availabilityEffect = effect(() => {
    const avail = this.availability();
    if (avail) {
      this.slots.set(avail.slots);
      this.subscribeToWebSocket(avail.courtId, avail.clubId);
    }
  });

  /**
   * The STOMP destination is per-club (/topic/clubs/{clubId}/availability), while
   * this screen is scoped to a single court. Subscribing with the courtId meant
   * the client listened on a topic nobody publishes to, so live updates never
   * arrived; clubId comes from the availability payload for exactly this.
   */
  private subscribeToWebSocket(courtId: number, clubId: number): void {
    if (this.unsubscribeWs) {
      this.unsubscribeWs();
    }
    this.ws.connect();
    this.unsubscribeWs = this.ws.subscribeToClubAvailability(clubId, (event: AvailabilityEvent) => {
      if (event.courtId !== courtId) return;
      this.handleAvailabilityEvent(event);
    });
  }

  private handleAvailabilityEvent(event: AvailabilityEvent): void {
    this.slots.update((currentSlots) =>
      currentSlots.map((slot) => {
        if (slot.start === event.slotStart && slot.end === event.slotEnd) {
          return { ...slot, available: event.type === 'SLOT_RELEASED' };
        }
        return slot;
      })
    );
  }

  protected formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  }

  protected formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' });
  }

  protected prevDay(): void {
    const current = new Date(this.date());
    current.setDate(current.getDate() - 1);
    this.navigateToDate(current);
  }

  protected nextDay(): void {
    const current = new Date(this.date());
    current.setDate(current.getDate() + 1);
    this.navigateToDate(current);
  }

  protected today(): void {
    this.navigateToDate(new Date());
  }

  private navigateToDate(date: Date): void {
    const iso = date.toISOString().split('T')[0];
    this.router.navigate(['/clubs', this.courtId(), 'availability', iso], { replaceUrl: true });
  }

  protected selectSlot(slot: Slot): void {
    if (!slot.available) return;
    // Clear the previous confirmation, otherwise a stale "Booking confirmed"
    // banner sits above the grid while the user books a different slot.
    this.success.set(null);
    this.error.set(null);
    this.selectedSlot.set(slot);
    this.showBookingDialog.set(true);
  }

  protected closeBookingDialog(): void {
    this.showBookingDialog.set(false);
    this.selectedSlot.set(null);
    this.bookingMode.set('book');
  }

  protected async confirmBooking(): Promise<void> {
    const slot = this.selectedSlot();
    if (!slot) return;

    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login'], { queryParams: { redirect: this.router.url } });
      return;
    }

    this.bookingLoading.set(true);
    const request: BookingRequest = {
      courtId: this.courtId()!,
      start: slot.start,
      end: slot.end,
    };

    try {
      if (this.bookingMode() === 'hold') {
        await firstValueFrom(this.bookingService.hold(request));
      } else {
        await firstValueFrom(this.bookingService.book(request));
      }
      this.closeBookingDialog();
      this.error.set(null);
      // Booking used to succeed completely silently: the dialog vanished and the
      // user was left guessing whether the slot was theirs. The .alert-success
      // styles already existed in the global stylesheet but nothing rendered them.
      this.success.set(
        this.bookingMode() === 'hold'
          ? 'Slot held. Complete payment before the hold expires.'
          : 'Booking confirmed. You can find it under My Bookings.',
      );
      this.router.navigate([], { relativeTo: this.route, replaceUrl: true });
    } catch (err: any) {
      if (err?.status === 409) {
        this.error.set('This slot was just taken by someone else. Refreshing…');
        setTimeout(() => this.router.navigate([], { relativeTo: this.route, replaceUrl: true }), 1000);
      } else if (err?.status === 422) {
        this.error.set(err.error?.message || 'Booking failed. Please check the slot and try again.');
      } else {
        this.error.set('Booking failed. Please try again.');
      }
    } finally {
      this.bookingLoading.set(false);
    }
  }

  ngOnDestroy(): void {
    if (this.unsubscribeWs) {
      this.unsubscribeWs();
    }
  }
}