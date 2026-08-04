import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Availability, Club, Court, OpeningHours, Page } from '../models/api.models';

/**
 * Read/write access to clubs, courts, opening hours and availability.
 *
 * The service owns URLs and HTTP details; components receive typed Observables and
 * never learn what the endpoints look like. That is what makes an API change a
 * one-file edit.
 */
@Injectable({ providedIn: 'root' })
export class ClubService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  search(query: string, city: string, page = 0, size = 12): Observable<Page<Club>> {
    // HttpParams is immutable: each set() returns a new instance. Building it this way
    // also gets URL-encoding right for free (a city like "Sidi Bou Saïd" just works).
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    if (city.trim()) {
      params = params.set('city', city.trim());
    }
    return this.http.get<Page<Club>>(`${this.api}/clubs`, { params });
  }

  getClub(id: number): Observable<Club> {
    return this.http.get<Club>(`${this.api}/clubs/${id}`);
  }

  getCourts(clubId: number): Observable<Court[]> {
    return this.http.get<Court[]>(`${this.api}/clubs/${clubId}/courts`);
  }

  getOpeningHours(clubId: number): Observable<OpeningHours[]> {
    return this.http.get<OpeningHours[]>(`${this.api}/clubs/${clubId}/opening-hours`);
  }

  /** The slot grid for one court on one day. `date` is 'yyyy-MM-dd'. */
  getAvailability(courtId: number, date: string): Observable<Availability> {
    const params = new HttpParams().set('date', date);
    return this.http.get<Availability>(`${this.api}/courts/${courtId}/availability`, { params });
  }
}
