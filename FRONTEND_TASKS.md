# CourtSide Frontend — Complete Task List

> Generated from a full inspection of the backend codebase (all controllers,
> services, entities, DTOs, migrations) and the current frontend source tree.
> Every task maps to a real, existing backend endpoint. Nothing here invents
> an API that doesn't exist yet.

---

## Current frontend state (what's already built)

| Feature | Status | Files |
|---|---|---|
| App shell (navbar, nav, footer, router outlet) | ✅ Done | `app.ts`, `app.html`, `app.scss` |
| Auth — login page (signal forms) | ✅ Done | `features/auth/login/` |
| Auth — register page (signal forms) | ✅ Done | `features/auth/register/` |
| Club list (debounced search + filter) | ✅ Done | `features/clubs/club-list/` |
| Auth service (signals-based, token storage) | ✅ Done | `core/services/auth.service.ts` |
| Booking service (book/hold/confirm/cancel/waitlist) | ✅ Done | `core/services/booking.service.ts` |
| Club service (search/get/courts/hours/availability) | ✅ Done | `core/services/club.service.ts` |
| Auth guard (any logged-in) | ✅ Done | `core/guards/auth.guard.ts` |
| Staff guard (MANAGER/ADMIN only) | ✅ Done | `core/guards/auth.guard.ts` — `staffGuard` exists but is **never imported** into a route |
| Auth HTTP interceptor (auto-refresh on 401) | ✅ Done | `core/interceptors/auth.interceptor.ts` |
| Token storage (SSR-safe localStorage wrapper) | ✅ Done | `core/services/token-storage.ts` |
| Typed API models (mirrors all backend DTOs) | ✅ Done | `core/models/api.models.ts` |
| Angular SSR setup (Express + prerender) | ✅ Done | `main.server.ts`, `app.config.server.ts`, `app.routes.server.ts` |
| Docker-aware dev proxy (`/api` → `:8080`, `/ws` → `:8080`) | ✅ Done | `proxy.conf.json` |
| Environment config (relative URLs, dev + prod) | ✅ Done | `environments/` |

---

## Missing frontend features — ordered by priority

### 1. Club detail page

**Why:** Every other feature builds on it. You can see a club in the list, but
can't open it to see its courts, opening hours, and availability.

| # | Task | Backend endpoint | Model | Priority |
|---|---|---|---|---|
| 1.1 | Create `club-detail` component | `GET /api/v1/clubs/{id}` → `ClubResponse` | `Club` | 🔴 High |
| 1.2 | Create `court-list` sub-component | `GET /api/v1/clubs/{clubId}/courts` → `Court[]` | `Court` | 🔴 High |
| 1.3 | Display courts with sport, slot minutes, price, active status | — | `Court` | 🔴 High |
| 1.4 | Link from club-list to club-detail (RouterLink) | — | — | 🔴 High |
| 1.5 | Add `/clubs/{id}` route to `app.routes.ts` (guarded for staff edit) | — | — | 🔴 High |
| 1.6 | Opening hours display | `GET /api/v1/clubs/{clubId}/opening-hours` → `OpeningHours[]` | `OpeningHours` | 🟡 Medium |
| 1.7 | Availability grid link (slot picker) → §2 | `GET /api/v1/courts/{courtId}/availability` | `Availability` | 🟡 Medium |

---

### 2. Availability grid + slot picker

**Why:** The core user experience — seeing which slots are free and clicking one.
This is the page that makes or breaks the booking flow.

| # | Task | Backend endpoint | Model | Priority |
|---|---|---|---|---|
| 2.1 | Create `availability-grid` component | `GET /api/v1/courts/{courtId}/availability?date=` | `Availability` | 🔴 High |
| 2.2 | Date picker (previous/next day navigation) | Uses `LocalDate` param | — | 🔴 High |
| 2.3 | Render slots: available (green), taken (grey), outside hours (dim) | `slots: Slot[]` where `Slot.available: boolean` | `Slot` | 🔴 High |
| 2.4 | Slot click → confirms slot is available → opens booking dialog | — | `BookingRequest` | 🔴 High |
| 2.5 | Integrate with `club-detail` (link from a court to its availability) | — | — | 🟡 Medium |
| 2.6 | Auto-refresh grid via WebSocket (§5) | WS `/topic/clubs/{clubId}/availability` | `AvailabilityEvent` | 🟡 Medium |

---

### 3. Booking flow

**Why:** This is the entire purpose of the application. The backend is 100%
complete; the frontend has zero booking UI.

| # | Task | Backend endpoint | DTO | Priority |
|---|---|---|---|---|
| 3.1 | Create `booking-dialog` component (book a slot) | `POST /api/v1/bookings` | `BookingRequest` | 🔴 High |
| 3.2 | Create `booking-dialog` component (hold a slot) | `POST /api/v1/bookings/hold` | `BookingRequest` | 🔴 High |
| 3.3 | Confirmation flow: HOLD → POST `/api/v1/bookings/{id}/confirm` | `BookingResponse` | — | 🔴 High |
| 3.4 | Handle 409 (slot taken) — show friendly "someone just took it" message | — | `ErrorResponse` | 🔴 High |
| 3.5 | Handle 422 (business rule: past, wrong duration, closed, not grid-aligned) | — | `ErrorResponse` | 🟡 Medium |
| 3.6 | Handle 403 (cancel window expired for members) | — | `ErrorResponse` | 🟡 Medium |

---

### 4. My bookings page

**Why:** Every authenticated user needs to see and manage their own bookings.
The route already exists (`/my-bookings`) with `authGuard`, but the component
is not yet created.

| # | Task | Backend endpoint | DTO | Priority |
|---|---|---|---|---|
| 4.1 | Create `my-bookings` component | `GET /api/v1/me/bookings?upcomingOnly=&page=&size=` | `Page<Booking>` | 🔴 High |
| 4.2 | Upcoming vs all toggle | `upcomingOnly` param | — | 🔴 High |
| 4.3 | Pagination (page/size, cap at 100) | `page`, `size` params | `PageResponse` | 🟡 Medium |
| 4.4 | Cancel booking (DELETE `/api/v1/bookings/{id}`) | Returns updated `BookingResponse` | `Booking` | 🔴 High |
| 4.5 | Show booking status badges (HOLD, CONFIRMED, CANCELLED, NO_SHOW) | — | `Booking.status` | 🟡 Medium |
| 4.6 | "Confirm" button for HOLD bookings | `POST /api/v1/bookings/{id}/confirm` | — | 🟡 Medium |
| 4.7 | Countdown timer for hold expiry | Derived from `holdExpiresAt` | — | 🟢 Low |

---

### 5. WebSocket live availability updates

**Why:** When someone books a slot, everyone watching that club's page should
see it turn red instantly — no refresh needed.

| # | Task | WebSocket endpoint | Event | Priority |
|---|---|---|---|---|
| 5.1 | Set up STOMP client in a core service | `GET /ws` (SockJS) | — | 🟡 Medium |
| 5.2 | Subscribe to `/topic/clubs/{clubId}/availability` | STOMP SUBSCRIBE | `AvailabilityEvent` | 🟡 Medium |
| 5.3 | On `SLOT_BOOKED`: mark the matching slot as unavailable | `type: 'SLOT_BOOKED'` | `AvailabilityEvent` | 🟡 Medium |
| 5.4 | On `SLOT_RELEASED`: mark the matching slot as available | `type: 'SLOT_RELEASED'` | `AvailabilityEvent` | 🟡 Medium |
| 5.5 | Unsubscribe on component destroy (memory management) | — | — | 🟢 Low |
| 5.6 | Graceful reconnect logic | — | — | 🟢 Low |

---

### 6. Manager dashboard

**Why:** Managers and admins need to see their club's performance — revenue,
occupancy, and busiest hours.

| # | Task | Backend endpoint | DTO | Priority |
|---|---|---|---|---|
| 6.1 | Create `manager-dashboard` component | `GET /api/v1/clubs/{clubId}/dashboard?from=&to=` | `DashboardResponse` | 🔴 High |
| 6.2 | Date range picker (defaults to last 30 days) | `from`, `to` params | `LocalDate` | 🔴 High |
| 6.3 | Display summary: confirmed/cancelled bookings, revenue, cancellation rate, capacity, occupancy | `Summary` | — | 🔴 High |
| 6.4 | Revenue by week chart/list | `revenueByWeek: WeekPoint[]` | `WeekPoint` | 🟡 Medium |
| 6.5 | Court performance table | `byCourt: CourtPerformance[]` | `CourtPerformance` | 🟡 Medium |
| 6.6 | Busiest hours bar | `busiestHours: HourPoint[]` | `HourPoint` | 🟡 Medium |
| 6.7 | Guard with `staffGuard` (only MANAGER/ADMIN can access) | — | — | 🔴 High |
| 6.8 | Add `/dashboard` route (nested under `/clubs/{id}`) | — | — | 🔴 High |

---

### 7. Club & court management (CRUD)

**Why:** Managers need to create clubs, add courts, and set opening hours.
The backend fully supports this; the frontend has no management UI.

#### 7a. Club management

| # | Task | Backend endpoint | DTO | Priority |
|---|---|---|---|---|
| 7a.1 | Create `club-create` component | `POST /api/v1/clubs` | `ClubRequest` | 🔴 High |
| 7a.2 | Create `club-edit` component (reuse `club-detail`) | `PUT /api/v1/clubs/{id}` | `ClubRequest` | 🔴 High |
| 7a.3 | Delete club (deactivation) | `DELETE /api/v1/clubs/{id}` | — | 🟡 Medium |
| 7a.4 | Guard with `staffGuard` | — | — | 🔴 High |

#### 7b. Court management

| # | Task | Backend endpoint | DTO | Priority |
|---|---|---|---|---|
| 7b.1 | Create court within a club | `POST /api/v1/clubs/{clubId}/courts` | `CourtRequest` | 🔴 High |
| 7b.2 | Edit court (name, sport, slot minutes, price) | `PUT /api/v1/courts/{id}` | `CourtRequest` | 🔴 High |
| 7b.3 | Deactivate court | `DELETE /api/v1/courts/{id}` | — | 🟡 Medium |
| 7b.4 | Sport selector (PADEL / TENNIS / SQUASH) | — | `Sport` | 🟡 Medium |

#### 7c. Opening hours management

| # | Task | Backend endpoint | DTO | Priority |
|---|---|---|---|---|
| 7c.1 | Opening hours editor (day-by-day upsert) | `PUT /api/v1/clubs/{clubId}/opening-hours` | `OpeningHoursRequest` | 🔴 High |
| 7c.2 | Delete a day's hours | `DELETE /api/v1/clubs/{clubId}/opening-hours/{dayOfWeek}` | — | 🟡 Medium |
| 7c.3 | Day-of-week selector (Mon–Sun) | — | `dayOfWeek: 1-7` | 🟡 Medium |

---

### 8. Waitlist UI

**Why:** Users need a way to join a waitlist for a full slot, and to see/manage
their waitlist entries. The backend has join/leave/list/notify — the frontend
has none of it.

| # | Task | Backend endpoint | DTO | Priority |
|---|---|---|---|---|
| 8.1 | "Join waitlist" button on the availability grid (when slot unavailable) | `POST /api/v1/waitlist` | `WaitlistRequest` | 🔴 High |
| 8.2 | `waitlist` service method | — | `WaitlistRequest` | 🔴 High |
| 8.3 | "My waitlist" page or panel | `GET /api/v1/me/waitlist` | `WaitlistResponse[]` | 🟡 Medium |
| 8.4 | Leave waitlist (DELETE `/api/v1/waitlist/{id}`) | — | — | 🟡 Medium |
| 8.5 | Show waitlist position / FIFO order | Derived from list order | — | 🟢 Low |

---

### 9. User profile / account

**Why:** Users should be able to see and edit their profile after registering.
The backend provides `GET /api/v1/me` but the frontend only loads it in the
app constructor — there's no profile page.

| # | Task | Backend endpoint | DTO | Priority |
|---|---|---|---|---|
| 9.1 | Create `profile` component | `GET /api/v1/me` | `Me` | 🟡 Medium |
| 9.2 | Display email, full name, role | — | `Me` | 🟡 Medium |
| 9.3 | Edit profile (full name) | — | — | 🟢 Low |
| 9.4 | Add `/my-account` or `/profile` route | — | — | 🟢 Low |

---

### 10. Navigation & routing completeness

| # | Task | Detail | Priority |
|---|---|---|---|
| 10.1 | Add all routes to `app.routes.ts` | `/clubs/{id}`, `/clubs/{id}/dashboard`, `/my-bookings`, `/waitlist`, `/profile` | 🔴 High |
| 10.2 | Wire `staffGuard` to staff-only routes | Manager dashboard, club/court/opening-hours CRUD | 🔴 High |
| 10.3 | Add nav links for staff (dashboard, manage clubs) | Conditionally shown based on `auth.isStaff()` | 🟡 Medium |
| 10.4 | Active link styling for new routes | `routerLinkActive` | 🟢 Low |
| 10.5 | 404 / "not found" route | Already handled by `path: '**'` → redirect to `clubs` | ✅ Done |

---

### 11. Error handling & UX polish

| # | Task | Detail | Priority |
|---|---|---|---|
| 11.1 | Global error handler for API errors | Catch `HttpErrorResponse`, display `ApiError.message`, field errors | 🔴 High |
| 11.2 | Loading spinners on all async operations | `loading` signals on every data-fetching component | 🟡 Medium |
| 11.3 | Empty state views ("no clubs found", "no bookings") | `catchError` → empty template | 🟡 Medium |
| 11.4 | Toast/snackbar notifications | For successful actions (booking confirmed, waitlist joined, etc.) | 🟡 Medium |
| 11.5 | Confirm dialog before cancel/delete | Prevent accidental destructive actions | 🟢 Low |
| 11.6 | Responsive mobile layout polish | Existing SCSS has mobile breakpoints; verify all new pages are responsive | 🟢 Low |

---

### 12. E2E tests (Playwright)

| # | Task | Detail | Priority |
|---|---|---|---|
| 12.1 | Install Playwright | `npm i -D @playwright/test` | 🔴 High |
| 12.2 | Auth flow test (register → login → logout) | Critical path | 🔴 High |
| 12.2 | Booking flow test (search club → view availability → book → confirm) | Critical path | 🔴 High |
| 12.3 | Concurrency race test (10 parallel booking requests) | Mirrors the backend race test — proves the 409 works | 🟡 Medium |
| 12.4 | Staff flow test (manager creates club + court + opens hours) | 🟡 Medium |
| 12.5 | CI integration (GitHub Actions matrix) | Run Playwright in CI alongside backend tests | 🟡 Medium |

---

### 13. Hardening & production readiness

| # | Task | Detail | Priority |
|---|---|---|---|
| 13.1 | Cache degradation handler | If Redis is down, availability should fall back to DB — frontend should handle slow/missing data gracefully | 🟢 Low |
| 13.2 | WebSocket multi-node | If scaled to multiple instances, switch from in-memory STOMP to Redis pub/sub — frontend needs no changes (same `/ws` endpoint) | 🟢 Low |
| 13.3 | Rate limiting awareness | Backend has no rate limit yet; frontend should implement request throttling on the search input | 🟢 Low |
| 13.4 | Environment variable for feature flags | e.g., `enableWaitlist`, `enableHold` — gate features behind config | 🟢 Low |
| 13.5 | Production build size audit | Current lazy loading gives ~99 kB initial / 25.6 kB transferred — verify no regression | 🟢 Low |

---

## Backend endpoint reference (complete contract)

### Authentication

| Method | Path | Auth | Returns |
|---|---|---|---|
| POST | `/api/v1/auth/register` | — | 201 + `{accessToken, refreshToken}` |
| POST | `/api/v1/auth/login` | — | 200 + `{accessToken, refreshToken}` |
| POST | `/api/v1/auth/refresh` | refresh token | 200 + **new** pair (rotation) |
| POST | `/api/v1/auth/logout` | refresh token | 204 |
| GET | `/api/v1/me` | access token | 200 + `{email, fullName, role}` |

### Clubs

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/clubs?q=&city=&page=&size=` | — | paginated `Club[]` |
| GET | `/api/v1/clubs/{id}` | — | `ClubResponse` |
| POST | `/api/v1/clubs` | ADMIN / MANAGER | 201 + `ClubResponse` |
| PUT | `/api/v1/clubs/{id}` | ADMIN / owning MANAGER | `ClubResponse` |
| DELETE | `/api/v1/clubs/{id}` | ADMIN | 204 |

### Courts

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/clubs/{clubId}/courts` | — | `CourtResponse[]` |
| GET | `/api/v1/courts/{id}` | — | `CourtResponse` |
| POST | `/api/v1/clubs/{clubId}/courts` | ADMIN / owning MANAGER | 201 + `CourtResponse` |
| PUT | `/api/v1/courts/{id}` | ADMIN / owning MANAGER | `CourtResponse` |
| DELETE | `/api/v1/courts/{id}` | ADMIN / owning MANAGER | 204 |

### Opening hours

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/clubs/{clubId}/opening-hours` | — | `OpeningHoursResponse[]` |
| PUT | `/api/v1/clubs/{clubId}/opening-hours` | ADMIN / owning MANAGER | `OpeningHoursResponse` |
| DELETE | `/api/v1/clubs/{clubId}/opening-hours/{dayOfWeek}` | ADMIN / owning MANAGER | 204 |

### Availability

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/courts/{courtId}/availability?date=` | — | `AvailabilityResponse` (full slot grid) |

### Bookings

| Method | Path | Auth | Returns |
|---|---|---|---|
| POST | `/api/v1/bookings` | any authenticated | 201 + `BookingResponse` (or 409) |
| POST | `/api/v1/bookings/hold` | any authenticated | 201 + `BookingResponse` |
| POST | `/api/v1/bookings/{id}/confirm` | any authenticated | `BookingResponse` (HOLD → CONFIRMED) |
| GET | `/api/v1/me/bookings?upcomingOnly=&page=&size=` | any authenticated | `PageResponse<BookingResponse>` |
| GET | `/api/v1/bookings/{id}` | owner / staff | `BookingResponse` |
| DELETE | `/api/v1/bookings/{id}` | owner / staff | `BookingResponse` (status → CANCELLED) |

### Waitlist

| Method | Path | Auth | Returns |
|---|---|---|---|
| POST | `/api/v1/waitlist` | any authenticated | 201 + `WaitlistResponse` (or 422) |
| GET | `/api/v1/me/waitlist` | any authenticated | `WaitlistResponse[]` |
| DELETE | `/api/v1/waitlist/{id}` | owner / ADMIN | 204 |

### Dashboard

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/clubs/{clubId}/dashboard?from=&to=` | ADMIN / owning MANAGER | `DashboardResponse` |

### Real-time (WebSocket)

| Method | Path | Auth | Description |
|---|---|---|---|
| WS | `/ws` (SockJS) | — | Subscribe to `/topic/clubs/{clubId}/availability` |
| — | Event: `SLOT_BOOKED` | — | `{type, clubId, courtId, date, slotStart, slotEnd}` |
| — | Event: `SLOT_RELEASED` | — | Same shape |

---

## Data model reference (for frontend type alignment)

### Enums

```typescript
type Role = 'MEMBER' | 'MANAGER' | 'ADMIN';
type Sport = 'PADEL' | 'TENNIS' | 'SQUASH';
type BookingStatus = 'HOLD' | 'CONFIRMED' | 'CANCELLED' | 'NO_SHOW';
```

### Key entities

**User**: `id`, `email`, `fullName`, `passwordHash`, `role`, `createdAt`

**Club**: `id`, `name`, `city`, `address`, `manager` (nullable → `User`), `createdAt`

**Court**: `id`, `club`, `name`, `sport` (default PADEL), `slotMinutes` (30–240),
`pricePerSlot` (NUMERIC 8,2), `active` (boolean), `version` (optimistic lock)

**Booking**: `id`, `court`, `user`, `startTime`, `endTime`, `status` (default CONFIRMED),
`price`, `holdExpiresAt` (nullable), `version`, `createdAt`

**WaitlistEntry**: `id`, `court`, `user`, `startTime`, `endTime`, `notifiedAt` (nullable),
`active` (boolean), `createdAt`

**OpeningHours**: `id`, `club`, `dayOfWeek` (1–7), `opens`, `closes` (LocalTime)

**RefreshToken**: `id`, `user`, `tokenHash` (SHA-256, 64 chars), `expiresAt`, `revoked`, `createdAt`

### Key DTOs

**BookingRequest**: `{ courtId, start, end }` — price and user come from server

**AvailabilityResponse**: `{ courtId, courtName, date, clubOpen, slots: Slot[] }`
where `Slot = { start, end, available, price }`

**DashboardResponse**: `{ clubId, from, to, summary, revenueByWeek, byCourt, busiestHours }`

---

## Suggested implementation order

```
1. Club detail page (1.1–1.7)     → foundation for everything else
2. Availability grid (2.1–2.6)     → the slot picker
3. Booking flow (3.1–3.6)          → the core action
4. My bookings (4.1–4.7)           → user-facing management
5. Navigation & routing (10.1–10.5)→ wire all pages together
6. Waitlist (8.1–8.5)              → complement to booking
7. WebSocket (5.1–5.6)             → real-time polish
8. Club/court/opening-hours CRUD   → staff tools
9. Manager dashboard (6.1–6.8)     → analytics
10. Profile (9.1–9.4)              → account management
11. Error handling & UX (11.1–11.6)→ polish
12. E2E tests (12.1–12.5)          → verification
13. Hardening (13.1–13.5)          → production readiness
```

---

## Notes

- **No new backend endpoints are needed.** Every task above maps to an existing,
  implemented endpoint. The frontend is the only missing piece.
- **Guard philosophy (from the docs):** Guards are UX, not security. They hide
  broken pages; the backend enforces authorization on every API call.
- **All DTOs are already typed** in `core/models/api.models.ts`. New DTOs
  (e.g., `DashboardResponse`, `OpeningHoursRequest`) need to be added there
  before implementing the corresponding features.
- **WebSocket events carry no private data** — only `{type, clubId, courtId,
  date, slotStart, slotEnd}`. No auth needed at the STOMP handshake.
- **The backend already handles:** price computation, ownership checks,
  validation, concurrency (exclusion constraint), soft delete, optimistic
  locking, pagination caps, and consistent error contracts. The frontend
  just needs to call the APIs and render the results.
