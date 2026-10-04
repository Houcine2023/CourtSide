# CourtSide — Local Development & Testing Guide

This document explains how to run the full stack (PostgreSQL + Redis + Spring Boot + Angular) and verify the implemented features.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **Docker Desktop** | Latest | Must be running |
| **Java** | 21 (JDK) | `java -version` |
| **Maven** | Wrapper included | `./mvnw` |
| **Node.js** | 20+ | `node --version` |
| **npm** | 10+ | `npm --version` |

Windows users: run commands in **PowerShell**.

---

## 1. Start Infrastructure (PostgreSQL + Redis + Adminer)

```powershell
cd CourtSide
docker compose up -d
docker compose ps
```

Wait until `courtside-db` shows **healthy**.

| Service | URL | Credentials |
|---|---|---|
| **PostgreSQL** | `localhost:5433` | user: `courtside`, pass: `courtside`, db: `courtside` |
| **Redis** | `localhost:6379` | — |
| **Adminer** | http://localhost:8081 | System: `PostgreSQL`, Server: `postgres`, User/Pass/DB: `courtside` |

> **Note:** Port 5433 is used because a native PostgreSQL service may already own 5432 on Windows.

---

## 2. Run the Backend (Spring Boot)

```powershell
cd CourtSide/backend
./mvnw.cmd spring-boot:run
```

Wait for the banner: `Started CourtsideApiApplication in X.XXX seconds`.

### Backend Health Check

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

### Backend API Base URL

```
http://localhost:8080/api/v1
```

---

## 3. Run the Frontend (Angular + SSR)

### Development Mode (with hot reload)

```powershell
cd CourtSide/frontend
npm install          # first time only
npm run start        # runs on http://localhost:4200
```

The dev server proxies `/api/*` and `/ws/*` to `http://localhost:8080` (see `proxy.conf.json`).

### Production Build + SSR Server

```powershell
cd CourtSide/frontend
npm run build
npm run serve:ssr:frontend   # runs on http://localhost:4000
```

---

## 4. Quick API Smoke Tests (PowerShell)

### Register a User

```powershell
$r = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/register `
  -ContentType "application/json" `
  -Body '{"email":"test@test.tn","password":"secret123","fullName":"Test User"}'
$r
# Expect: 201 + { accessToken, refreshToken }
```

### Login

```powershell
$r = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/login `
  -ContentType "application/json" `
  -Body '{"email":"test@test.tn","password":"secret123"}'
$r
# Expect: 200 + { accessToken, refreshToken }
$accessToken = $r.accessToken
$refreshToken = $r.refreshToken
```

### Get Current User Profile

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/me `
  -Headers @{ Authorization = "Bearer $accessToken" }
# Expect: 200 + { email, fullName, role }
```

### Search Clubs (Public)

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/clubs?q=padel&city=tunis&page=0&size=10"
# Expect: paginated ClubResponse[]
```

### Get Club Details

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/clubs/1"
# Expect: ClubResponse
```

### Get Courts for a Club

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/clubs/1/courts"
# Expect: CourtResponse[]
```

### Get Opening Hours

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/clubs/1/opening-hours"
# Expect: OpeningHoursResponse[]
```

### Get Availability for a Court (Today)

```powershell
$today = (Get-Date).ToString("yyyy-MM-dd")
Invoke-RestMethod "http://localhost:8080/api/v1/courts/1/availability?date=$today"
# Expect: AvailabilityResponse { courtId, courtName, date, clubOpen, slots[] }
```

### Create a Booking (Authenticated)

```powershell
# First get a slot from the availability response, then:
$bookingReq = @{
  courtId = 1
  start   = "2026-09-20T18:00:00+01:00"   # ISO-8601 with offset
  end     = "2026-09-20T19:30:00+01:00"
} | ConvertTo-Json

Invoke-RestMethod -Method Post http://localhost:8080/api/v1/bookings `
  -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer $accessToken" } `
  -Body $bookingReq
# Expect: 201 + BookingResponse (or 409 if slot taken)
```

### Get My Bookings

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/me/bookings `
  -Headers @{ Authorization = "Bearer $accessToken" }
# Expect: PageResponse<BookingResponse>
```

### Refresh Access Token

```powershell
$new = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/refresh `
  -ContentType "application/json" `
  -Body (@{ refreshToken = $refreshToken } | ConvertTo-Json)
$new
# Expect: 200 + NEW { accessToken, refreshToken } (old refresh token revoked)
```

### Logout

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/logout `
  -ContentType "application/json" `
  -Body (@{ refreshToken = $new.refreshToken } | ConvertTo-Json)
# Expect: 204 No Content
```

---

## 5. Frontend Manual Testing Checklist

Open http://localhost:4200 (or 4000 for SSR build) and verify:

### ✅ Implemented Features

| Feature | Route | What to Test |
|---|---|---|
| **Club List** | `/clubs` | Search by name/city, debounced input, pagination |
| **Club Detail** | `/clubs/{id}` | Club info, opening hours table, court cards with sport icons |
| **Court List** | (embedded in club detail) | Active/inactive toggle, "Check availability" links |
| **My Bookings** | `/my-bookings` (auth required) | Upcoming/all toggle, pagination, status badges, cancel/confirm buttons |
| **Login** | `/login` | Signal form validation, error handling, redirect after login |
| **Register** | `/register` | Signal form validation, 409 conflict handling |

### Navigation Flow

1. **Home** → redirects to `/clubs`
2. **Clubs page** → click a club card → `/clubs/{id}`
3. **Club detail** → click "Check availability" on a court → (not yet implemented, will go to availability grid)
4. **Navbar** → "My bookings" (requires login) → `/my-bookings`
5. **Navbar** → "Sign in" → `/login` → after login → back to previous page or `/clubs`

### Auth State Persistence

- Refresh the page: tokens persist in `localStorage`, navbar shows user name
- Wait 15+ minutes: access token expires, interceptor auto-refreshes via refresh token
- Use refresh token twice: second use → 401, all sessions revoked (reuse detection)

---

## 6. Database Inspection (Adminer)

1. Open http://localhost:8081
2. Login: System `PostgreSQL`, Server `postgres`, User `courtside`, Password `courtside`, Database `courtside`
3. Useful tables to inspect:
   - `users` — registered accounts
   - `clubs`, `courts`, `opening_hours` — club data
   - `bookings` — bookings with `status`, `hold_expires_at`
   - `waitlist_entries` — waitlist queue
   - `refresh_tokens` — hashed tokens (`token_hash`), `revoked` flag

---

## 7. Common Issues & Fixes

| Problem | Solution |
|---|---|
| `docker compose up` fails: port 5432/5433 in use | Stop native PostgreSQL service: `Stop-Service postgresql-x64-17` |
| Backend fails to start: `Flyway checksum mismatch` | Run `docker compose down -v` to wipe DB volume, then `docker compose up -d` |
| Frontend build: `Cannot find module '...component'` | Ensure component files are named `*.component.ts` and imports match directory structure |
| Frontend: `NG8002: Can't bind to 'field'` | Signal forms in Angular 22 don't use `Field` directive; use `[value]="form.field().value()"` + `(input)="form.field().value.set($event.target.value)"` |
| SSR build: `getPrerenderParams missing for 'clubs/:id'` | Add route to `app.routes.server.ts` with `renderMode: RenderMode.Server` |
| 401 on every API call after login | Check `Authorization: Bearer <token>` header; verify token not expired; check interceptor network tab |
| Booking returns 409 | Slot already taken — expected behavior due to PostgreSQL exclusion constraint |

---

## 8. Useful Backend Endpoints for Testing

| Category | Endpoint | Auth | Notes |
|---|---|---|---|
| **Auth** | `POST /api/v1/auth/register` | — | Returns token pair |
|  | `POST /api/v1/auth/login` | — | Returns token pair |
|  | `POST /api/v1/auth/refresh` | refresh token | Returns **new** pair (rotation) |
|  | `POST /api/v1/auth/logout` | refresh token | 204 |
|  | `GET /api/v1/me` | access token | Current user profile |
| **Clubs** | `GET /api/v1/clubs` | — | Query: `q`, `city`, `page`, `size` |
|  | `GET /api/v1/clubs/{id}` | — | Single club |
|  | `POST /api/v1/clubs` | ADMIN/MANAGER | Create club |
|  | `PUT /api/v1/clubs/{id}` | ADMIN/owner MANAGER | Update club |
| **Courts** | `GET /api/v1/clubs/{id}/courts` | — | `?includeInactive=true` for staff |
|  | `GET /api/v1/courts/{id}` | — | Single court |
|  | `POST /api/v1/clubs/{id}/courts` | ADMIN/owner MANAGER | Create court |
|  | `PUT /api/v1/courts/{id}` | ADMIN/owner MANAGER | Update court |
|  | `DELETE /api/v1/courts/{id}` | ADMIN/owner MANAGER | Deactivate (soft delete) |
| **Opening Hours** | `GET /api/v1/clubs/{id}/opening-hours` | — | Public read |
|  | `PUT /api/v1/clubs/{id}/opening-hours` | ADMIN/owner MANAGER | Upsert one day |
|  | `DELETE /api/v1/clubs/{id}/opening-hours/{dow}` | ADMIN/owner MANAGER | Remove day |
| **Availability** | `GET /api/v1/courts/{id}/availability?date=` | — | Full day slot grid |
| **Bookings** | `POST /api/v1/bookings` | any user | 201 or 409 |
|  | `POST /api/v1/bookings/hold` | any user | 201, auto-expires in 10 min |
|  | `POST /api/v1/bookings/{id}/confirm` | any user | HOLD → CONFIRMED |
|  | `GET /api/v1/me/bookings` | any user | `?upcomingOnly=&page=&size=` |
|  | `DELETE /api/v1/bookings/{id}` | owner/staff | Cancel (status → CANCELLED) |
| **Waitlist** | `POST /api/v1/waitlist` | any user | Join queue |
|  | `GET /api/v1/me/waitlist` | any user | My entries |
|  | `DELETE /api/v1/waitlist/{id}` | owner/ADMIN | Leave queue |
| **Dashboard** | `GET /api/v1/clubs/{id}/dashboard` | ADMIN/owner MANAGER | Revenue, occupancy, busiest hours |
| **WebSocket** | `WS /ws` → SUB `/topic/clubs/{id}/availability` | — | `SLOT_BOOKED` / `SLOT_RELEASED` |

---

## 9. Stopping Everything

```powershell
# Stop frontend: Ctrl+C in its terminal
# Stop backend:  Ctrl+C in its terminal

# Stop infrastructure (keeps DB data in volume):
docker compose down

# Stop infrastructure AND wipe DB:
docker compose down -v
```

---

## 10. Project Structure Quick Reference

```
CourtSide/
├── backend/                 # Spring Boot 4.1, Java 21
│   ├── src/main/java/com/courtside/api/
│   │   ├── controllers/     # REST endpoints
│   │   ├── services/        # Business logic
│   │   ├── entities/        # JPA entities (mirror Flyway)
│   │   ├── repositories/    # Spring Data JPA
│   │   ├── dtos/            # Request/Response records
│   │   ├── security/        # JWT filter, SecurityConfig
│   │   └── config/          # WebSocket, Cache, etc.
│   └── src/main/resources/db/migration/
│       ├── V1__init_schema.sql      # Core schema + exclusion constraint
│       └── V2__holds_and_waitlist.sql # Holds, waitlist, partial indexes
│
├── frontend/                # Angular 22, SSR, Signals
│   ├── src/app/
│   │   ├── core/
│   │   │   ├── guards/          # authGuard, staffGuard
│   │   │   ├── interceptors/    # authInterceptor (auto-refresh)
│   │   │   ├── models/          # api.models.ts (typed DTOs)
│   │   │   └── services/        # AuthService, BookingService, ClubService
│   │   └── features/
│   │       ├── auth/            # login, register
│   │       ├── clubs/           # club-list, club-detail, court-list
│   │       └── bookings/        # my-bookings
│   └── proxy.conf.json         # Dev proxy /api → :8080, /ws → :8080
│
├── docker-compose.yml         # Postgres, Redis, Adminer
└── FRONTEND_TASKS.md          # Remaining work breakdown
```