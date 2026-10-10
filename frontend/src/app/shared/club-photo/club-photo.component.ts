import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';

/**
 * Renders a club's photo, or a placeholder tile when it has none.
 *
 * The API always returns a photoUrl, even for a club that has never had a photo
 * uploaded — answering that would cost a query per row on every listing. So the
 * request is always made and a missing photo arrives as a normal image error,
 * which this component turns into a tile bearing the club's initial. The result
 * is that every card has artwork without the listing endpoint needing to know
 * anything about photos.
 *
 * Shared because three surfaces show club cards (landing, directory, my-clubs)
 * and they should degrade in exactly the same way.
 */
@Component({
  selector: 'app-club-photo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './club-photo.component.html',
  styleUrl: './club-photo.component.scss',
})
export class ClubPhotoComponent {
  /** The photo address, as returned by ClubResponse.photoUrl. */
  readonly src = input.required<string>();
  /** Used as the alt text while the image loads and resolves. */
  readonly alt = input.required<string>();
  /**
   * The card grid is the first thing painted on the landing and directory pages,
   * so the first row of photos is the Largest Contentful Paint candidate. Mark
   * it priority (eager + high fetch priority) instead of lazy, or Angular warns
   * NG0913 about deferring the LCP image.
   */
  readonly priority = input(false);

  protected readonly failed = signal(false);
  /** First character of the alt text — what the placeholder tile shows. */
  protected readonly initial = computed(() => this.alt().trim().charAt(0).toUpperCase() || '?');

  constructor() {
    // A photo can be uploaded while this component is already on screen (the
    // my-clubs card). Without the reset the tile would stay up, having decided
    // the old URL was broken, and the new photo would never be attempted.
    effect(() => {
      this.src();
      this.failed.set(false);
    });
  }
}
