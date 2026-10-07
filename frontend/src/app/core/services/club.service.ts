import { HttpClient, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Availability, Club, ClubRequest, Court, CourtRequest, DashboardResponse, OpeningHours, OpeningHoursRequest, Page } from '../models/api.models';

/**
 * Read/write access to clubs, courts, opening hours and availability.
 *
 * The service owns URLs and HTTP details; components receive typed Observables and
 * never learn what the endpoints look like. That is what makes an API change a
 * one-file edit.
 */
@Service()
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

  getCourts(clubId: number, includeInactive = false): Observable<Court[]> {
    const params = new HttpParams().set('includeInactive', includeInactive);
    return this.http.get<Court[]>(`${this.api}/clubs/${clubId}/courts`, { params });
  }

  getOpeningHours(clubId: number): Observable<OpeningHours[]> {
    return this.http.get<OpeningHours[]>(`${this.api}/clubs/${clubId}/opening-hours`);
  }

  /** The slot grid for one court on one day. `date` is 'yyyy-MM-dd'. */
  getAvailability(courtId: number, date: string): Observable<Availability> {
    const params = new HttpParams().set('date', date);
    return this.http.get<Availability>(`${this.api}/courts/${courtId}/availability`, { params });
  }

  /** Manager dashboard: revenue, occupancy, court performance, busiest hours. */
  getDashboard(clubId: number, from?: string, to?: string): Observable<DashboardResponse> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<DashboardResponse>(`${this.api}/clubs/${clubId}/dashboard`, { params });
  }

  /** Create a new club (ADMIN/MANAGER). */
  create(request: ClubRequest): Observable<Club> {
    return this.http.post<Club>(`${this.api}/clubs`, request);
  }

  /** Update a club (ADMIN/owner MANAGER). */
  update(id: number, request: ClubRequest): Observable<Club> {
    return this.http.put<Club>(`${this.api}/clubs/${id}`, request);
  }

  /** Delete a club (ADMIN only). */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/clubs/${id}`);
  }

  /**
   * Replace a club's photo (ADMIN / owning MANAGER).
   *
   * FormData rather than a JSON body: the file has to travel as multipart. The
   * browser sets the Content-Type itself (including the boundary), so nothing
   * sets it here — overriding it is the classic way to break an upload. The auth
   * interceptor still attaches the token, because the URL is under environment.apiUrl.
   */
  uploadPhoto(clubId: number, file: File): Observable<void> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.put<void>(`${this.api}/clubs/${clubId}/photo`, form);
  }

  /** Remove a club's photo. The server treats it as idempotent. */
  deletePhoto(clubId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/clubs/${clubId}/photo`);
  }

  /** Court CRUD */
  createCourt(clubId: number, request: CourtRequest): Observable<Court> {
    return this.http.post<Court>(`${this.api}/clubs/${clubId}/courts`, request);
  }

  updateCourt(courtId: number, request: CourtRequest): Observable<Court> {
    return this.http.put<Court>(`${this.api}/courts/${courtId}`, request);
  }

  deactivateCourt(courtId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/courts/${courtId}`);
  }

  /** Opening Hours CRUD */
  setOpeningHours(clubId: number, request: OpeningHoursRequest): Observable<OpeningHours> {
    return this.http.put<OpeningHours>(`${this.api}/clubs/${clubId}/opening-hours`, request);
  }

  deleteOpeningHours(clubId: number, dayOfWeek: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/clubs/${clubId}/opening-hours/${dayOfWeek}`);
  }
}
