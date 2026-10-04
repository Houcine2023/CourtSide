import { Component, inject, Injectable, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface Toast {
  id: number;
  type: 'success' | 'error' | 'info' | 'warning';
  message: string;
  duration?: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _toasts = signal<Toast[]>([]);
  private idCounter = 0;

  readonly toasts = computed(() => this._toasts());

  success(message: string, duration = 4000): number {
    return this.add('success', message, duration);
  }

  error(message: string, duration = 6000): number {
    return this.add('error', message, duration);
  }

  info(message: string, duration = 4000): number {
    return this.add('info', message, duration);
  }

  warning(message: string, duration = 5000): number {
    return this.add('warning', message, duration);
  }

  private add(type: Toast['type'], message: string, duration: number): number {
    const id = ++this.idCounter;
    const toast: Toast = { id, type, message, duration };
    this._toasts.update((list) => [...list, toast]);

    if (duration > 0) {
      setTimeout(() => this.remove(id), duration);
    }
    return id;
  }

  remove(id: number): void {
    this._toasts.update((list) => list.filter((t) => t.id !== id));
  }

  clear(): void {
    this._toasts.set([]);
  }
}

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (toasts().length > 0) {
      <div class="toast-container" role="region" aria-live="polite" aria-label="Notifications">
        @for (toast of toasts(); track toast.id) {
          <div class="toast" [class]="'toast-' + toast.type">
            <div class="toast-icon" aria-hidden="true">
              @switch (toast.type) {
                @case ('success') { ✓ }
                @case ('error') { ✕ }
                @case ('warning') { ⚠ }
                @default { ℹ }
              }
            </div>
            <div class="toast-message">{{ toast.message }}</div>
            <button class="toast-close" (click)="remove(toast.id)" aria-label="Dismiss">✕</button>
          </div>
        }
      </div>
    }
  `,
  styleUrl: './toast.service.scss',
})
export class ToastContainerComponent {
  private readonly toastService = inject(ToastService);
  protected readonly toasts = this.toastService.toasts;
  protected remove = (id: number) => this.toastService.remove(id);
}