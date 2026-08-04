import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, of, startWith, switchMap, tap } from 'rxjs';

import { Club, Page } from '../../core/models/api.models';
import { ClubService } from '../../core/services/club.service';

@Component({
  selector: 'app-club-list',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="page">
      <header class="page-head">
        <h1>Find a club</h1>
        <p class="muted">Browse clubs and book a court — no account needed to look around.</p>
      </header>

      <div class="filters">
        <input
          type="search"
          placeholder="Search by name…"
          [formControl]="queryControl"
          aria-label="Search clubs by name" />
        <input
          type="text"
          placeholder="City"
          [formControl]="cityControl"
          aria-label="Filter by city" />
      </div>

      @if (loading()) {
        <p class="muted">Searching…</p>
      } @else if (result(); as page) {
        @if (page.content.length === 0) {
          <p class="empty">No club matches that search.</p>
        } @else {
          <p class="muted">{{ page.totalElements }} club(s) found</p>
          <div class="card-grid">
            @for (club of page.content; track club.id) {
              <a class="card" [routerLink]="['/clubs', club.id]">
                <h2>{{ club.name }}</h2>
                <p class="city">{{ club.city }}</p>
                <p class="muted">{{ club.address }}</p>
                @if (club.managerName) {
                  <p class="badge">Managed by {{ club.managerName }}</p>
                }
              </a>
            }
          </div>
        }
      } @else {
        <p class="empty">Could not load clubs. Is the API running?</p>
      }
    </section>
  `,
})
export class ClubListComponent implements OnInit {
  private readonly clubService = inject(ClubService);
  // Must be a field initialiser: inject() only works in an injection context, which
  // ngOnInit is not.
  private readonly destroyRef = inject(DestroyRef);

  readonly queryControl = new FormControl('', { nonNullable: true });
  readonly cityControl = new FormControl('', { nonNullable: true });

  readonly loading = signal(false);
  readonly result = signal<Page<Club> | null>(null);

  ngOnInit(): void {
    /**
     * The canonical typeahead pipeline. Each operator earns its place:
     *
     *  startWith('')          – run once on load, before anyone types
     *  debounceTime(300)      – wait for a pause; "padel" is 1 request, not 5
     *  distinctUntilChanged() – ignore edits that leave the text unchanged
     *  switchMap              – THE important one: cancels the previous request when
     *                           a new term arrives. With mergeMap, a slow response for
     *                           "pa" could land after the fast one for "padel" and
     *                           overwrite the results with stale data.
     *  takeUntilDestroyed     – unsubscribes when the component dies (no leak)
     */
    const query$ = this.queryControl.valueChanges.pipe(startWith(this.queryControl.value));
    const city$ = this.cityControl.valueChanges.pipe(startWith(this.cityControl.value));

    combineLatest([query$, city$])
      .pipe(
        debounceTime(300),
        distinctUntilChanged(
          ([prevQ, prevCity], [nextQ, nextCity]) => prevQ === nextQ && prevCity === nextCity,
        ),
        tap(() => this.loading.set(true)),
        switchMap(([query, city]) =>
          this.clubService.search(query, city).pipe(
            // Catch INSIDE the inner observable: an error caught outside switchMap
            // would kill the outer stream and the search box would stop working
            // forever after the first failure.
            catchError(() => of(null)),
          ),
        ),
        tap(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => this.result.set(page));
  }
}
