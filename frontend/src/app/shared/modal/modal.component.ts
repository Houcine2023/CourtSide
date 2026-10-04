import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';

/**
 * The single modal implementation for the whole app.
 *
 * Every create/edit/confirm surface renders through here so that behaviour and
 * accessibility are identical everywhere instead of being re-implemented (and
 * drifting) per feature:
 *
 *  - Esc closes, Tab is trapped inside, the backdrop closes
 *  - focus moves into the dialog on open and is restored to the trigger on close
 *  - background scrolling is locked while open
 *  - rendered in the native top layer, so it is never clipped by an ancestor's
 *    `overflow` or stacking context (the reason hand-rolled dialogs kept
 *    appearing inline at the bottom of the page)
 *  - a labelled dialog for screen readers
 */
@Component({
  selector: 'app-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './modal.component.html',
  styleUrl: './modal.component.scss',
})
export class ModalComponent {
  /** Heading text. Also becomes the dialog's accessible name. */
  readonly heading = input.required<string>();
  /** Optional supporting copy shown under the heading. */
  readonly description = input<string>();
  /** Set while a submit is in flight to block dismissal. */
  readonly busy = input(false);
  /** Hides the backdrop click-to-close and Esc when false, for destructive confirms. */
  readonly dismissible = input(true);
  /** Emits on close button, Esc, backdrop, or an explicit dismiss(). */
  readonly closed = output<void>();

  private readonly dialogRef = viewChild.required<ElementRef<HTMLElement>>('dialog');
  /** Unique per instance so several dialogs can be labelled independently. */
  protected readonly labelId = `modal-${Math.random().toString(36).slice(2, 9)}`;
  private previouslyFocused: HTMLElement | null = null;

  constructor() {
    afterNextRender(() => {
      const dialog = this.nativeDialog();
      if (!dialog) return;
      this.previouslyFocused = document.activeElement as HTMLElement | null;
      dialog.showModal();
      // Focus the first control so keyboard users land inside the dialog,
      // falling back to the dialog itself when it has no fields.
      const focusable = dialog.querySelector<HTMLElement>(
        'input:not([type=hidden]), select, textarea, button:not(.modal-close)',
      );
      (focusable ?? dialog).focus();
    });

    // Returning focus to the control that opened the dialog keeps keyboard
    // users on the page they were on instead of at the top of the document.
    inject(DestroyRef).onDestroy(() => this.previouslyFocused?.focus());
  }

  protected nativeDialog(): HTMLDialogElement | null {
    return (this.dialogRef()?.nativeElement as HTMLDialogElement) ?? null;
  }

  /** Called from the template for the backdrop click and the close button. */
  protected dismiss(): void {
    if (this.busy()) return;
    this.closed.emit();
  }

  /**
   * The <dialog> element fills the viewport, so a click on the dialog element
   * itself is a click on the backdrop. Clicks inside the panel bubble up with a
   * different target and are ignored, which avoids the stopPropagation wiring
   * every hand-rolled dialog needed.
   */
  protected onBackdropClick(event: MouseEvent): void {
    if (event.target !== this.nativeDialog()) return;
    this.dismiss();
  }

  /**
   * Esc is cancelled so the dialog only closes via dismiss(), which lets the
   * parent veto while a save is in flight.
   */
  protected onCancel(event: Event): void {
    event.preventDefault();
    this.dismiss();
  }

  /** Keeps Tab cycling within the dialog instead of escaping to the page behind it. */
  protected onKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;

    const dialog = this.nativeDialog();
    if (!dialog) return;

    const focusable = Array.from(
      dialog.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([type=hidden]):not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ),
    ).filter((el) => el.offsetParent !== null);

    if (focusable.length === 0) {
      event.preventDefault();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;

    if (event.shiftKey && (active === first || active === dialog)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }
}
