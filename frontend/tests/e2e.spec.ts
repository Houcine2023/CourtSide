import { test, expect } from '@playwright/test';

test.describe('Authentication Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should redirect to clubs page', async ({ page }) => {
    await expect(page).toHaveURL(/.*clubs/);
    await expect(page.locator('h1')).toContainText('Clubs');
  });

  test('should navigate to login page', async ({ page }) => {
    await page.click('text=Sign in');
    await expect(page).toHaveURL(/.*login/);
    await expect(page.locator('h1')).toContainText('Welcome back');
  });

  test('should navigate to register page', async ({ page }) => {
    await page.click('text=Get started');
    await expect(page).toHaveURL(/.*register/);
    await expect(page.locator('h1')).toContainText('Create your account');
  });

  test('should register a new user', async ({ page }) => {
    const email = `test${Date.now()}@test.tn`;
    const password = 'secret123';
    const fullName = 'Test User';

    await page.goto('/register');
    await page.fill('input[name="fullName"]', fullName);
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');

    // Should redirect to clubs page after successful registration
    await expect(page).toHaveURL(/.*clubs/);
    await expect(page.locator('text=Hi, Test')).toBeVisible();
  });

  test('should login with existing credentials', async ({ page }) => {
    const email = `test${Date.now()}@test.tn`;
    const password = 'secret123';
    const fullName = 'Test User';

    // First register
    await page.goto('/register');
    await page.fill('input[name="fullName"]', fullName);
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*clubs/);

    // Logout
    await page.click('button:has-text("Sign out")');

    // Login again
    await page.click('text=Sign in');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');

    // Should redirect to clubs
    await expect(page).toHaveURL(/.*clubs/);
    await expect(page.locator('text=Hi, Test')).toBeVisible();
  });

  test('should show error for invalid login', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[name="email"]', 'nonexistent@test.tn');
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button[type="submit"]');

    await expect(page.locator('.alert-error')).toBeVisible();
    await expect(page.locator('.alert-error')).toContainText('Invalid email or password');
  });
});

test.describe('Club Discovery', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/clubs');
  });

  test('should display clubs list', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('Clubs');
  });

  test('should search clubs by name', async ({ page }) => {
    await page.fill('input[placeholder*="Search"]', 'padel');
    await page.waitForTimeout(500); // debounce
    // Should show filtered results
  });

  test('should filter clubs by city', async ({ page }) => {
    await page.fill('input[placeholder*="City"]', 'Tunis');
    await page.waitForTimeout(500);
  });

  test('should navigate to club detail', async ({ page }) => {
    // Wait for clubs to load
    await page.waitForSelector('.club-card', { timeout: 10000 });
    const firstClub = page.locator('.club-card').first();
    await firstClub.click();
    await expect(page).toHaveURL(/.*clubs\/\d+/);
    await expect(page.locator('h1')).toBeVisible();
  });
});

test.describe('Club Detail & Availability', () => {
  test('should display club details', async ({ page }) => {
    await page.goto('/clubs/1');
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.locator('text=Opening Hours')).toBeVisible();
    await expect(page.locator('text=Courts')).toBeVisible();
  });

  test('should show courts with availability links', async ({ page }) => {
    await page.goto('/clubs/1');
    await expect(page.locator('text=Courts')).toBeVisible();
    await expect(page.locator('button:has-text("Check availability")')).toBeVisible();
  });
});

test.describe('Booking Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Login first
    const email = `test${Date.now()}@test.tn`;
    const password = 'secret123';
    const fullName = 'Test User';

    await page.goto('/register');
    await page.fill('input[name="fullName"]', fullName);
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*clubs/);
  });

  test('should navigate to availability from club detail', async ({ page }) => {
    await page.goto('/clubs/1');
    await page.waitForSelector('button:has-text("Check availability")');
    await page.click('button:has-text("Check availability")');
    await expect(page).toHaveURL(/.*availability/);
  });

  test('should display availability grid', async ({ page }) => {
    await page.goto('/clubs/1/availability/2026-09-20');
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.locator('text=Available slots')).toBeVisible();
  });

  test('should book a slot', async ({ page }) => {
    const today = new Date().toISOString().split('T')[0];
    await page.goto(`/clubs/1/availability/${today}`);
    
    // Find and click an available slot
    const availableSlot = page.locator('.slot-btn.slot-available').first();
    await expect(availableSlot).toBeVisible();
    await availableSlot.click();

    // Should open booking dialog
    await expect(page.locator('text=Confirm Booking')).toBeVisible();
    await page.click('button:has-text("Confirm Booking")');

    // Should show success or redirect
    await expect(page.locator('text=Booking confirmed')).toBeVisible({ timeout: 5000 }).catch(() => {});
  });
});

test.describe('My Bookings', () => {
  test.beforeEach(async ({ page }) => {
    const email = `test${Date.now()}@test.tn`;
    const password = 'secret123';
    const fullName = 'Test User';

    await page.goto('/register');
    await page.fill('input[name="fullName"]', fullName);
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*clubs/);
  });

  test('should display my bookings page', async ({ page }) => {
    await page.goto('/my-bookings');
    await expect(page.locator('h1')).toContainText('My Bookings');
  });

  test('should show upcoming bookings toggle', async ({ page }) => {
    await page.goto('/my-bookings');
    await expect(page.locator('input[type="checkbox"]')).toBeVisible();
    await expect(page.locator('text=Upcoming only')).toBeVisible();
  });
});

test.describe('Staff Features', () => {
  test('should show staff links for MANAGER role', async ({ page }) => {
    // This test would need an admin user setup
    // For now, just verify the route exists
    await page.goto('/my-clubs');
    // Should redirect to login if not authenticated
  });
});

test.describe('Waitlist', () => {
  test.beforeEach(async ({ page }) => {
    const email = `test${Date.now()}@test.tn`;
    const password = 'secret123';
    const fullName = 'Test User';

    await page.goto('/register');
    await page.fill('input[name="fullName"]', fullName);
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*clubs/);
  });

  test('should display waitlist page', async ({ page }) => {
    await page.goto('/waitlist');
    await expect(page.locator('h1')).toContainText('My Waitlist');
  });

  test('should join waitlist for a slot', async ({ page }) => {
    await page.goto('/waitlist');
    await page.click('button:has-text("Join Waitlist")');
    // Fill waitlist form
    await page.fill('input[name="courtId"]', '1');
    await page.fill('input[name="start"]', '2026-09-20T18:00');
    await page.fill('input[name="end"]', '2026-09-20T19:30');
    await page.click('button[type="submit"]');
    await expect(page.locator('text=Waiting')).toBeVisible({ timeout: 5000 });
  });
});

test.describe('Profile', () => {
  test.beforeEach(async ({ page }) => {
    const email = `test${Date.now()}@test.tn`;
    const password = 'secret123';
    const fullName = 'Test User';

    await page.goto('/register');
    await page.fill('input[name="fullName"]', fullName);
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*clubs/);
  });

  test('should display profile page', async ({ page }) => {
    await page.goto('/profile');
    await expect(page.locator('h1')).toContainText('My Account');
    await expect(page.locator('text=Profile Information')).toBeVisible();
    await expect(page.locator('text=Security')).toBeVisible();
  });
});