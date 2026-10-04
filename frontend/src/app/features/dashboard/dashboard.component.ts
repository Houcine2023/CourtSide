import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map, of, catchError, switchMap } from 'rxjs';

import { ClubService } from '../../core/services/club.service';
import { AuthService } from '../../core/services/auth.service';
import { DashboardResponse, DashboardSummary, WeekPoint, CourtPerformance, HourPoint } from '../../core/models/api.models';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly clubService = inject(ClubService);
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly fromDate = signal<string>('');
  protected readonly toDate = signal<string>('');
  protected readonly dashboardData = signal<DashboardResponse | null>(null);

  protected readonly dashboard = computed(() => this.dashboardData());

  private readonly clubId = toSignal(this.route.paramMap.pipe(
    map((params) => Number(params.get('clubId')))
  ));

  constructor() {
    effect(() => {
      const clubId = this.clubId();
      const from = this.fromDate();
      const to = this.toDate();
      if (clubId) {
        this.loadDashboard(clubId, from, to);
      }
    });
  }

  private loadDashboard(clubId: number, from: string, to: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.clubService.getDashboard(clubId, from || undefined, to || undefined).pipe(
      map((data) => ({ data, failed: false })),
      catchError(() => of({ data: null as DashboardResponse | null, failed: true })),
    ).subscribe((result) => {
      this.loading.set(false);
      if (result.failed) {
        this.error.set('Failed to load dashboard.');
      } else {
        this.dashboardData.set(result.data);
      }
    });
  }

  protected readonly summary = computed(() => this.dashboard()?.summary);
  protected readonly revenueByWeek = computed(() => this.dashboard()?.revenueByWeek ?? []);
  protected readonly byCourt = computed(() => this.dashboard()?.byCourt ?? []);
  protected readonly busiestHours = computed(() => this.dashboard()?.busiestHours ?? []);

  protected readonly defaultFrom = computed(() => {
    const d = new Date();
    d.setDate(d.getDate() - 30);
    return d.toISOString().split('T')[0];
  });

  protected readonly defaultTo = computed(() => new Date().toISOString().split('T')[0]);

  protected onFromChange(event: Event): void {
    this.fromDate.set((event.target as HTMLInputElement).value);
  }

  protected onToChange(event: Event): void {
    this.toDate.set((event.target as HTMLInputElement).value);
  }

  protected applyDateRange(): void {
    // Trigger refetch
    this.loadDashboard(this.clubId()!, this.fromDate(), this.toDate());
  }

  protected resetDateRange(): void {
    this.fromDate.set(this.defaultFrom());
    this.toDate.set(this.defaultTo());
    this.applyDateRange();
  }

  protected formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-TN', { style: 'currency', currency: 'TND', minimumFractionDigits: 2 }).format(value);
  }

  protected formatPercent(value: number): string {
    return value.toFixed(1) + '%';
  }

  protected formatWeek(iso: string): string {
    const date = new Date(iso);
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  protected getMaxRevenue(): number {
    return Math.max(...this.revenueByWeek().map((w: WeekPoint) => w.revenue), 1);
  }

  protected getMaxCourtRevenue(): number {
    return Math.max(...this.byCourt().map((c: CourtPerformance) => c.revenue), 1);
  }

  protected getMaxHourBookings(): number {
    return Math.max(...this.busiestHours().map((h: HourPoint) => h.bookings), 1);
  }
}