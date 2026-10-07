import { test, expect } from '@playwright/test';

/**
 * End-to-end coverage of the flows a real user walks.
 *
 * These run against the live stack (Spring Boot on :8080, Angular on :4200) with
 * the seeded V3/V4 demo data, so they exercise real auth, real bookings and real
 * cache behaviour rather than mocks.
 *
 * Selector notes, since they are the usual source of rot here:
 * - The auth forms bind with `id` and signal inputs, NOT `name`, so they are
 *   addressed as #fullName / #email / #password.
 * - Club discovery lives on the landing page. /clubs is now just the full grid.
 * - Availability is addressed by COURT id (/clubs/:courtId/availability/:date),
 *   not club id.
 */

const PASSWORD = 'secret123';
let seq = 0;

/** Unique per test so parallel workers cannot collide on the unique email index. */
const uniqueEmail = () => `e2e${Date.now()}${seq++}@test.tn`;

/**
 * Navigates and waits for hydration to finish.
 *
 * The app is server-rendered, so the DOM exists before Angular is listening.
 * Every [ngh] marker is removed as its component hydrates, so an empty set plus a
 * bootstrapped `window.ng` means the page is genuinely interactive. Tests that
 * skip this race the server HTML and flake rather than fail.
 */
async function open(page: import('@playwright/test').Page, path: string) {
  await page.goto(path);
  await page.waitForFunction(() => {
    const w = window as unknown as { ng?: unknown };
    return typeof w.ng !== 'undefined' && document.querySelectorAll('[ngh]').length === 0;
  });
  return page;
}

/**
 * How many clubs the API actually serves.
 *
 * Read from the API instead of hardcoded. These assertions used to expect 6 cards
 * and only passed because an earlier run had left an extra club behind in a dirty
 * local database; a freshly seeded one has five, so CI failed where dev did not.
 */
async function clubCount(request: import('@playwright/test').APIRequestContext) {
  const res = await request.get('/api/v1/clubs?page=0&size=1');
  const body = (await res.json()) as { totalElements: number };
  return body.totalElements;
}

async function register(page: import('@playwright/test').Page, fullName = 'Test User') {
  const email = uniqueEmail();
  await open(page, '/register');
  await page.fill('#fullName', fullName);
  await page.fill('#email', email);
  await page.fill('#password', PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/clubs');
  return { email, password: PASSWORD, fullName };
}

// ---------------------------------------------------------------- landing page

test.describe('Landing page', () => {
  test.beforeEach(async ({ page }) => open(page, '/'));

  test('is the root route and introduces the product', async ({ page }) => {
    await expect(page).toHaveURL(/\/$/);
    await expect(page.locator('h1')).toContainText('Book a court');
    await expect(page.getByRole('heading', { name: /how it works/i })).toBeVisible();
  });

  test('renders real club cards from the API', async ({ page, request }) => {
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    // One card per club the API serves, rather than an empty or truncated grid.
    await expect(page.locator('.club-card')).toHaveCount(await clubCount(request));
  });

  test('derives the headline stats from data', async ({ page }) => {
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    const stats = page.locator('.hero-stats strong');
    await expect(stats).toHaveCount(4);
    for (let i = 0; i < 4; i++) {
      await expect(stats.nth(i)).toHaveText(/^\d+$/);
    }
  });

  test('filters clubs by city chip', async ({ page }) => {
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    await page.getByRole('button', { name: 'Sousse', exact: true }).first().click();
    await expect(page.locator('.club-card')).toHaveCount(1);
    await expect(page.locator('.club-card')).toContainText('Sousse');
  });

  test('filters clubs by sport chip', async ({ page }) => {
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    await page.getByRole('button', { name: /Squash/ }).first().click();
    const cards = page.locator('.club-card');
    // Multi-Sport Club Sousse also has a squash court, so the filter is a real
    // match on court data rather than a substring match on the club name.
    await expect(cards).toHaveCount(2);
    await expect(cards.first()).toContainText(/squash/i);
  });

  test('narrows results with the finder search box', async ({ page }) => {
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    await page.fill('#club-search', 'hammamet');
    await expect(page.locator('.club-card')).toHaveCount(1);
    await expect(page.locator('.club-card')).toContainText('Hammamet');
  });

  test('shows an empty state rather than a broken grid for no matches', async ({ page }) => {
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    await page.fill('#club-search', 'zzzz-no-such-club');
    await expect(page.locator('.club-card')).toHaveCount(0);
    await expect(page.getByText(/no clubs match/i)).toBeVisible();
  });

  test('does not overflow horizontally on a phone viewport', async ({ page }) => {
    await page.setViewportSize({ width: 393, height: 851 });
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test('links a club card straight to availability', async ({ page }) => {
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    await page.getByRole('link', { name: /check availability/i }).first().click();
    await expect(page).toHaveURL(/\/clubs\/\d+\/availability\/\d{4}-\d{2}-\d{2}/);
  });
});

// -------------------------------------------------------------------- auth

test.describe('Authentication', () => {
  test.beforeEach(async ({ page }) => open(page, '/'));

  test('navigates to sign in', async ({ page }) => {
    // Scoped to the navbar: the landing page also has a "Sign in" call to action,
    // so an unscoped getByRole would match two links and fail in strict mode.
    await page.locator('#primary-nav').getByRole('link', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/.*login/);
    await expect(page.locator('h1')).toContainText('Welcome back');
  });

  test('navigates to registration', async ({ page }) => {
    await page.locator('#primary-nav').getByRole('link', { name: 'Get started' }).click();
    await expect(page).toHaveURL(/.*register/);
    await expect(page.locator('h1')).toContainText('Create your account');
  });

  test('registers a new user and lands in the app', async ({ page }) => {
    const { fullName } = await register(page);
    await expect(page.getByText(`Hi, ${fullName.split(' ')[0]}`)).toBeVisible();
  });

  test('rejects a weak password without calling the API', async ({ page }) => {
    await open(page, '/register');
    await page.fill('#fullName', 'Weak Pass');
    await page.fill('#email', uniqueEmail());
    await page.fill('#password', 'short');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*register/);
    await expect(page.locator('.field-error').first()).toBeVisible();
  });

  test('signs in again after signing out', async ({ page }) => {
    const { email, fullName } = await register(page);

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page.locator('#primary-nav').getByRole('link', { name: 'Sign in' })).toBeVisible();

    await page.locator('#primary-nav').getByRole('link', { name: 'Sign in' }).click();
    await page.fill('#email', email);
    await page.fill('#password', PASSWORD);
    await page.click('button[type="submit"]');

    await page.waitForURL('**/clubs');
    await expect(page.getByText(`Hi, ${fullName.split(' ')[0]}`)).toBeVisible();
  });

  test('reports bad credentials', async ({ page }) => {
    await open(page, '/login');
    await page.fill('#email', 'nobody@test.tn');
    await page.fill('#password', 'definitely-wrong');
    await page.click('button[type="submit"]');
    await expect(page.locator('.alert-error')).toBeVisible();
    await expect(page).toHaveURL(/.*login/);
  });
});

// ------------------------------------------------------------- club discovery

test.describe('Club directory', () => {
  test.beforeEach(async ({ page }) => open(page, '/clubs'));

    test('lists every club', async ({ page, request }) => {
    await expect(page.locator('.club-card')).toHaveCount(await clubCount(request), { timeout: 15000 });
  });

  test('exposes a page heading and a sane heading order', async ({ page }) => {
    // The directory used to start straight at the club cards with no h1 at all,
    // so the page had no accessible name and the cards skipped a level to h3.
    await expect(page.locator('h1')).toHaveText('Find a club');
    await expect(page.getByRole('heading', { level: 2 }).first()).toBeVisible();
  });

  test('opens a club from its card', async ({ page }) => {
    await expect(page.locator('.club-card').first()).toBeVisible({ timeout: 15000 });
    await page.locator('.club-card').first().click();
    await expect(page).toHaveURL(/\/clubs\/\d+/);
    await expect(page.locator('h1')).not.toBeEmpty();
  });

  test('shows courts and opening hours on the detail page', async ({ page }) => {
    await open(page, '/clubs/1');
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Courts' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Opening Hours' })).toBeVisible();
  });

  test('offers an availability link per court', async ({ page }) => {
    await open(page, '/clubs/1');
    await expect(page.getByRole('heading', { name: 'Courts' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Check availability' }).first()).toBeVisible();
  });
});

// ------------------------------------------------------------------- booking

test.describe('Booking', () => {
  test.beforeEach(async ({ page }) => {
    await register(page);
  });

  test('reaches availability from the club page', async ({ page }) => {
    await open(page, '/clubs/1');
    await page.getByRole('link', { name: 'Check availability' }).first().click();
    await expect(page).toHaveURL(/\/availability\//);
  });

  test('renders a slot grid for a court', async ({ page }) => {
    const today = new Date().toISOString().split('T')[0];
    await open(page, `/clubs/1/availability/${today}`);
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.locator('.slot-btn').first()).toBeVisible();
  });

  test('books a free slot and shows it under my bookings', async ({ page }) => {
    // Book into the future on a randomly chosen court/date rather than on today's
    // first free slot. Today's grid legitimately shrinks as the day wears on, and
    // fullyParallel lets sibling tests claim the same slot, so a fixed court/date
    // made this test pass or fail depending on the hour it ran.
    const courtId = 1 + Math.floor(Math.random() * 3);
    const date = new Date(Date.now() + (3 + Math.floor(Math.random() * 25)) * 86_400_000)
      .toISOString()
      .split('T')[0];
    await open(page, `/clubs/${courtId}/availability/${date}`);

    const slot = page.locator('.slot-btn.slot-available').first();
    await expect(slot).toBeVisible();
    await slot.click();

    // The confirm dialog is the shared modal, so it renders a real <dialog>.
    const dialog = page.locator('dialog[open]');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /^Confirm/ }).click();

    // Confirming only fires a POST; it does not block navigation. Without waiting
    // for the request to land, the next open() could reach /my-bookings before the
    // booking was persisted and the list would render empty.
    await expect(dialog).toBeHidden({ timeout: 15000 });
    await expect(page.locator('.toast, .alert-success').first()).toBeVisible({ timeout: 15000 });

    await open(page, '/my-bookings');
    await expect(page.locator('h1')).toContainText('My Bookings');
    await expect(page.locator('.booking-card').first()).toBeVisible({ timeout: 15000 });
  });
});

test.describe('My bookings', () => {
  test.beforeEach(async ({ page }) => {
    await register(page);
    await open(page, '/my-bookings');
  });

  test('renders the page with the upcoming filter', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('My Bookings');
    await expect(page.locator('input[type="checkbox"]')).toBeVisible();
    await expect(page.getByText('Upcoming only')).toBeVisible();
  });

  test('toggles the upcoming filter', async ({ page }) => {
    const box = page.locator('input[type="checkbox"]');
    // Defaults to showing everything, past bookings included.
    await expect(box).not.toBeChecked();
    await box.check();
    await expect(box).toBeChecked();
    await box.uncheck();
    await expect(box).not.toBeChecked();
  });
});

// ------------------------------------------------------------------ waitlist

test.describe('Waitlist', () => {
  test.beforeEach(async ({ page }) => {
    await register(page);
  });

  test('renders the page', async ({ page }) => {
    await open(page, '/waitlist');
    await expect(page.locator('h1')).toContainText('My Waitlist');
  });

  test('opens the join dialog from a shared modal', async ({ page }) => {
    await open(page, '/waitlist');
    await page.getByRole('button', { name: /join waitlist/i }).first().click();
    const dialog = page.locator('dialog[open]');
    await expect(dialog).toBeVisible();
    await expect(dialog.locator('#waitlistCourtId')).toBeVisible();
  });

  test('closes the dialog on Escape without joining', async ({ page }) => {
    await open(page, '/waitlist');
    await page.getByRole('button', { name: /join waitlist/i }).first().click();
    await expect(page.locator('dialog[open]')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('dialog[open]')).toHaveCount(0);
  });
});

// ------------------------------------------------------------------- profile

test.describe('Profile', () => {
  test.beforeEach(async ({ page }) => {
    await register(page);
  });

  test('shows the account sections', async ({ page }) => {
    await open(page, '/profile');
    await expect(page.locator('h1')).toContainText('My Account');
    await expect(page.getByRole('heading', { name: 'Profile Information' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Security' })).toBeVisible();
  });

  test('updates the display name', async ({ page }) => {
    await open(page, '/profile');
    await page.fill('#fullName', 'Renamed Person');
    await page.getByRole('button', { name: /save/i }).click();
    await expect(page.getByText('Renamed')).toBeVisible({ timeout: 15000 });
  });

  test('opens the password dialog', async ({ page }) => {
    await open(page, '/profile');
    await page.getByRole('button', { name: /change password/i }).click();
    await expect(page.locator('dialog[open]')).toBeVisible();
    await expect(page.locator('#currentPassword')).toBeVisible();
  });
});

// --------------------------------------------------------------- permissions

test.describe('Role-based navigation', () => {
  test('hides manager links from anonymous visitors', async ({ page }) => {
    await open(page, '/');
    await expect(page.getByRole('link', { name: 'My Clubs' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Dashboard' })).toHaveCount(0);
  });

  test('hides manager links from a regular member', async ({ page }) => {
    await register(page);
    await expect(page.getByRole('link', { name: 'My Clubs' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Dashboard' })).toHaveCount(0);
  });

  test('guards the manager route from a regular member', async ({ page }) => {
    await register(page);
    await open(page, '/my-clubs');
    // staffGuard must bounce a member away from the management area.
    await expect(page).not.toHaveURL(/\/my-clubs/);
  });
});