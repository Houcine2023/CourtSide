import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { toObservable } from '@angular/core/rxjs-interop';

import { Club } from '../../../core/models/api.models';
import { ClubService } from '../../../core/services/club.service';

/**
 * Club discovery: a debounced search over the public /clubs endpoint.
 *
 * The search term is a signal (bound with two-way syntax in the template), converted
 * to a stream for the debounce/cancel logic, then back to a signal for rendering.
 * Signals are excellent at "current value"; RxJS is excellent at "values over time" —
 * this uses each for what it is good at instead of forcing one to do both.
 */
@Component({
  selector: 'app-club-list',
  imports: [RouterLink],
  templateUrl: './club-list.component.html',
  styleUrl: './club-list.component.scss',
})
export class ClubListComponent {
  private readonly clubService = inject(ClubService);

  protected readonly query = signal('');
  protected readonly city = signal('');
  protected readonly loading = signal(false);

  private readonly searchResult = toSignal(
    // combineLatest over the two inputs, expressed with signals -> observable.
    toObservable(computed(() => ({ q: this.query(), city: this.city() }))).pipe(
      startWith({ q: '', city: '' }),
      debounceTime(300),
      distinctUntilChanged((a, b) => a.q === b.q && a.city === b.city),
      switchMap((criteria) => {
        this.loading.set(true);
        return this.clubService.search(criteria.q, criteria.city).pipe(
          map((page) => ({ clubs: page.content, total: page.totalElements, failed: false })),
          // catchError INSIDE switchMap: caught outside, one failed request would
          // complete the outer stream and the search box would die permanently.
          catchError(() => of({ clubs: [] as Club[], total: 0, failed: true })),
        );
      }),
      map((result) => {
        this.loading.set(false);
        return result;
      }),
    ),
    { initialValue: { clubs: [] as Club[], total: 0, failed: false } },
  );

  protected readonly clubs = computed(() => this.searchResult().clubs);
  protected readonly total = computed(() => this.searchResult().total);
  protected readonly failed = computed(() => this.searchResult().failed);
  protected readonly hasFilters = computed(() => this.query().length > 0 || this.city().length > 0);

  protected updateQuery(value: string): void {
    this.query.set(value);
  }

  protected updateCity(value: string): void {
    this.city.set(value);
  }

  protected clearFilters(): void {
    this.query.set('');
    this.city.set('');
  }
}
