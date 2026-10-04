import { Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { switchMap, map, of, catchError, combineLatest } from 'rxjs';

import { Court } from '../../../core/models/api.models';
import { ClubService } from '../../../core/services/club.service';

@Component({
  selector: 'app-court-list',
  imports: [RouterLink],
  templateUrl: './court-list.component.html',
  styleUrl: './court-list.component.scss',
})
export class CourtListComponent {
  readonly clubId = input.required<number>();
  readonly clubName = input.required<string>();
  readonly showManage = input(false);

  /**
   * Bump this in the parent after any court mutation to refetch the list.
   * Without it a deactivated court keeps showing until a full page reload,
   * because the original fetch only ran once, on input change.
   */
  readonly reloadToken = input(0);

  readonly manageCourt = output<Court>();
  readonly deactivateCourt = output<number>();

  private readonly clubService = inject(ClubService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly today = new Date().toISOString().split('T')[0];

  protected readonly courts = toSignal(
    // combineLatest over the id and the reload token: switchMap then discards
    // any in-flight request when either changes, so a stale response can never
    // overwrite fresher data.
    combineLatest([toObservable(this.clubId), toObservable(this.reloadToken)]).pipe(
      switchMap(([id]) => this.clubService.getCourts(id, true).pipe(
        map((courts) => ({ courts, failed: false })),
        catchError(() => of({ courts: [] as Court[], failed: true })),
        map((result) => {
          this.loading.set(false);
          if (result.failed) {
            this.error.set('Failed to load courts.');
          } else {
            this.error.set(null);
          }
          return result.courts;
        }),
      )),
    ),
    { initialValue: [] as Court[] },
  );

  protected readonly activeCourts = computed(() =>
    this.courts().filter((c: Court) => c.active),
  );

  protected readonly inactiveCourts = computed(() =>
    this.courts().filter((c: Court) => !c.active),
  );

  protected readonly sportIcons: Record<string, string> = {
    PADEL: '🏓',
    TENNIS: '🎾',
    SQUASH: '🏸',
  };

  protected formatPrice(price: number): string {
    return price.toFixed(2);
  }

  protected getSportIcon(sport: string): string {
    return this.sportIcons[sport] ?? '🏟️';
  }

  protected availabilityLink(courtId: number): string[] {
    return ['/clubs', String(courtId), 'availability', this.today];
  }
}