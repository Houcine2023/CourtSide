import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ModalComponent } from './modal.component';

/**
 * The modal is the one place every create/edit/confirm surface shares, so these
 * tests target the contract the features depend on: when it closes, when it
 * refuses to, and how it behaves for keyboard and screen-reader users.
 *
 * jsdom 28 does not implement HTMLDialogElement.showModal(), so it is stubbed
 * below. The component itself calls the real method in a browser.
 */

/** Installs the minimum dialog surface jsdom is missing. */
function stubDialogApi() {
  const proto = window.HTMLDialogElement.prototype as unknown as Record<string, unknown>;
  const calls: string[] = [];
  proto['showModal'] = vi.fn(function (this: HTMLDialogElement) {
    calls.push('showModal');
    this.open = true;
  });
  proto['close'] = vi.fn(function (this: HTMLDialogElement) {
    calls.push('close');
    this.open = false;
    this.dispatchEvent(new Event('close'));
  });
  return calls;
}

async function render(inputs: Partial<{ heading: string; description: string; busy: boolean; dismissible: boolean }> = {}) {
  const fixture: ComponentFixture<ModalComponent> = TestBed.createComponent(ModalComponent);
  fixture.componentRef.setInput('heading', inputs.heading ?? 'Edit club');
  if (inputs.description !== undefined) fixture.componentRef.setInput('description', inputs.description);
  fixture.componentRef.setInput('busy', inputs.busy ?? false);
  fixture.componentRef.setInput('dismissible', inputs.dismissible ?? true);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

const dialog = (fixture: ComponentFixture<ModalComponent>) =>
  fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;

describe('ModalComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    stubDialogApi();
    TestBed.configureTestingModule({ imports: [ModalComponent] });
  });

  it('labels the dialog with its heading for screen readers', async () => {
    const fixture = await render({ heading: 'Edit club' });
    const el = dialog(fixture);
    const labelId = el.getAttribute('aria-labelledby')!;
    expect(labelId).toBeTruthy();
    expect(fixture.nativeElement.querySelector(`#${labelId}`)?.textContent).toContain('Edit club');
    expect(el.getAttribute('aria-modal')).toBe('true');
  });

  it('describes itself only when a description is supplied', async () => {
    const withDesc = await render({ heading: 'Edit club', description: 'Changes save immediately' });
    expect(dialog(withDesc).getAttribute('aria-describedby')).toBeTruthy();

    const without = await render({ heading: 'Edit club' });
    expect(dialog(without).getAttribute('aria-describedby')).toBeNull();
  });

  it('opens itself in the top layer on first render', async () => {
    const fixture = await render();
    expect(dialog(fixture).open).toBe(true);
  });

  it('emits closed from the close button', async () => {
    const fixture = await render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    (fixture.nativeElement.querySelector('.modal-close') as HTMLButtonElement).click();
    expect(closed).toHaveBeenCalledTimes(1);
  });

  it('hides the close button when it is not dismissible', async () => {
    const fixture = await render({ dismissible: false });
    expect(fixture.nativeElement.querySelector('.modal-close')).toBeNull();
  });

  it('routes Esc through dismiss so the parent can veto it', async () => {
    const fixture = await render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    const event = new Event('cancel', { cancelable: true });
    dialog(fixture).dispatchEvent(event);

    // preventDefault is what stops the browser closing the dialog on its own,
    // leaving the decision with the parent.
    expect(event.defaultPrevented).toBe(true);
    expect(closed).toHaveBeenCalledTimes(1);
  });

  it('refuses to close while a submit is in flight', async () => {
    const fixture = await render({ busy: true });
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    // Esc is the path that used to get through while saving.
    dialog(fixture).dispatchEvent(new Event('cancel', { cancelable: true }));
    expect(closed).not.toHaveBeenCalled();
  });

  it('closes on a backdrop click but not on a click inside the panel', async () => {
    const fixture = await render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    // The <dialog> fills the viewport, so a click on the dialog element itself is
    // a click on the backdrop.
    dialog(fixture).dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(closed).toHaveBeenCalledTimes(1);

    // Clicks inside bubble up with a different target and must be ignored.
    fixture.nativeElement.querySelector('.modal-body')!.dispatchEvent(
      new MouseEvent('click', { bubbles: true }),
    );
    expect(closed).toHaveBeenCalledTimes(1);
  });

  it('ignores backdrop clicks while busy', async () => {
    const fixture = await render({ busy: true });
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    dialog(fixture).dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(closed).not.toHaveBeenCalled();
  });

  it('labels every instance separately so stacked dialogs stay distinguishable', async () => {
    const a = await render({ heading: 'First' });
    const b = await render({ heading: 'Second' });
    expect(dialog(a).getAttribute('aria-labelledby')).not.toBe(
      dialog(b).getAttribute('aria-labelledby'),
    );
  });
});