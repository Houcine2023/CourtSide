import { TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { AvailabilityComponent } from './availability.component';
import { ClubService } from '../../../core/services/club.service';
import { BookingService } from '../../../core/services/booking.service';
import { AuthService } from '../../../core/services/auth.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { Availability, Slot } from '../../../core/models/api.models';

/** Slot helper: "today at HH:mm" in the past or future, relative to now. */
function slotAt(hour: number, available = true): Slot {
  const start = new Date();
  start.setHours(hour, 0, 0, 0);
  const end = new Date(start);
  end.setMinutes(end.getMinutes() + 90);
  return {
    id: start.getTime(),
    start: start.toISOString(),
    end: end.toISOString(),
    available,
    price: 45,
  } as Slot;
}

function build(slots: Slot[]): Availability {
  return {
    courtId: 1,
    courtName: 'Court Central',
    clubId: 1,
    date: '2026-01-01',
    clubOpen: true,
    slots,
  } as Availability;
}

async function setup(slots: Slot[]) {
  TestBed.configureTestingModule({
    imports: [AvailabilityComponent],
    providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ courtId: '1', date: '2026-01-01' })) } },
      { provide: ClubService, useValue: { getAvailability: vi.fn().mockReturnValue(of(build(slots))) } },
      { provide: BookingService, useValue: { book: vi.fn(), hold: vi.fn() } },
      { provide: AuthService, useValue: { isLoggedIn: vi.fn().mockReturnValue(true) } },
      {
        provide: WebSocketService,
        useValue: {
          connect: vi.fn(),
          subscribeToClubAvailability: vi.fn().mockReturnValue(() => {}),
        },
      },
    ],
  });

  const fixture = TestBed.createComponent(AvailabilityComponent);
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

describe('AvailabilityComponent slot filtering', () => {
  it('drops slots that have already started even when the API calls them available', async () => {
    const now = new Date();
    const pastHour = Math.max(0, now.getHours() - 2);

    const fixture = await setup([slotAt(pastHour, true)]);

    // The API reports availability from opening hours alone, so this slot comes
    // back available even though it started hours ago. Booking it could only ever
    // end in a 422, so it must not be offered.
    const available = (fixture.componentInstance as any).availableSlots();
    expect(available).toHaveLength(0);
    expect((fixture.componentInstance as any).unavailableSlots()).toHaveLength(1);
  });

  it('keeps future slots the API reports as available', async () => {
    const now = new Date();
    const futureHour = now.getHours() + 3;

    const fixture = await setup([slotAt(futureHour, true)]);

    expect((fixture.componentInstance as any).availableSlots()).toHaveLength(1);
  });

  it('keeps an already booked slot out of the available list', async () => {
    const now = new Date();
    const futureHour = now.getHours() + 4;

    const fixture = await setup([slotAt(futureHour, false)]);

    expect((fixture.componentInstance as any).availableSlots()).toHaveLength(0);
    expect((fixture.componentInstance as any).isSlotPast(slotAt(futureHour, false))).toBe(false);
  });

  it('labels a past slot Past rather than Booked', async () => {
    const now = new Date();
    const pastHour = Math.max(0, now.getHours() - 2);
    const past = slotAt(pastHour, true);

    const fixture = await setup([past]);

    expect((fixture.componentInstance as any).isSlotPast(past)).toBe(true);
  });
});