import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Booking, BookingRequest, Page } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  /** Books a slot outright. 409 when someone else got there first. */
  book(request: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>(`${this.api}/bookings`, request);
  }

  /** Reserves the slot while a payment completes; auto-released if never confirmed. */
  hold(request: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>(`${this.api}/bookings/hold`, request);
  }

  confirm(bookingId: number): Observable<Booking> {
    return this.http.post<Booking>(`${this.api}/bookings/${bookingId}/confirm`, {});
  }

  myBookings(upcomingOnly = false, page = 0, size = 20): Observable<Page<Booking>> {
    const params = new HttpParams()
      .set('upcomingOnly', upcomingOnly)
      .set('page', page)
      .set('size', size);
    return this.http.get<Page<Booking>>(`${this.api}/me/bookings`, { params });
  }

  /** DELETE cancels (status change) and returns the updated booking. */
  cancel(bookingId: number): Observable<Booking> {
    return this.http.delete<Booking>(`${this.api}/bookings/${bookingId}`);
  }

  joinWaitlist(request: BookingRequest): Observable<unknown> {
    return this.http.post(`${this.api}/waitlist`, request);
  }
}
