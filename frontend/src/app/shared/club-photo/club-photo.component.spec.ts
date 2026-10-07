import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ClubPhotoComponent } from './club-photo.component';

/**
 * Every club card renders one of these, and the API always answers with a photo
 * address — even for a club that has no photo, which arrives here as an ordinary
 * image error. These tests pin down the two things the cards depend on: the photo
 * loads, and a club without one still gets a tile instead of a broken-image icon.
 */

async function render(src = '/api/v1/clubs/1/photo', alt = 'Padel Club Tunis') {
  const fixture: ComponentFixture<ClubPhotoComponent> = TestBed.createComponent(ClubPhotoComponent);
  fixture.componentRef.setInput('src', src);
  fixture.componentRef.setInput('alt', alt);
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

const img = (fixture: ComponentFixture<ClubPhotoComponent>) =>
  fixture.nativeElement.querySelector('img') as HTMLImageElement | null;

const tile = (fixture: ComponentFixture<ClubPhotoComponent>) =>
  fixture.nativeElement.querySelector('.club-photo-fallback') as HTMLElement | null;

/** Fires the image's error path, which is what a missing photo looks like. */
async function failPhoto(fixture: ComponentFixture<ClubPhotoComponent>) {
  img(fixture)!.dispatchEvent(new Event('error'));
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('ClubPhotoComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [ClubPhotoComponent] });
  });

  it('shows the photo with the club name as alt text', async () => {
    const fixture = await render();
    expect(img(fixture)?.getAttribute('src')).toBe('/api/v1/clubs/1/photo');
    expect(img(fixture)?.getAttribute('alt')).toBe('Padel Club Tunis');
  });

  it('defers loading the photo until it scrolls into view', async () => {
    const fixture = await render();
    expect(img(fixture)?.getAttribute('loading')).toBe('lazy');
    expect(img(fixture)?.getAttribute('decoding')).toBe('async');
  });

  it('swaps to a placeholder tile bearing the club initial when the photo fails', async () => {
    const fixture = await render();
    await failPhoto(fixture);

    expect(img(fixture)).toBeNull();
    const fallback = tile(fixture);
    expect(fallback).not.toBeNull();
    expect(fallback?.textContent?.trim()).toBe('P');
    // Decorative: the card's heading already names the club.
    expect(fallback?.getAttribute('aria-hidden')).toBe('true');
  });

  it('uppercases the initial whatever case the club name uses', async () => {
    const fixture = await render('/api/v1/clubs/2/photo', 'tennis club la marsa');
    await failPhoto(fixture);
    expect(tile(fixture)?.textContent?.trim()).toBe('T');
  });

  it('tries again when the photo address changes after a failure', async () => {
    const fixture = await render();
    await failPhoto(fixture);
    expect(tile(fixture)).not.toBeNull();

    fixture.componentRef.setInput('src', '/api/v1/clubs/1/photo?v=1728');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(tile(fixture)).toBeNull();
    expect(img(fixture)?.getAttribute('src')).toBe('/api/v1/clubs/1/photo?v=1728');
  });
});
