# CourtSide 🎾

Sports-facility booking platform — **Angular 22 (SSR)** frontend · **Spring Boot 4** API ·
**PostgreSQL** · **Redis**. Flagship project: conflict-safe bookings enforced by a PostgreSQL
exclusion constraint, live availability pushed over WebSocket.

![CI](https://github.com/Houcine2023/CourtSide/actions/workflows/ci.yml/badge.svg)

## What it does

- **Members** browse clubs on a map list, see an availability grid, book slots
  (free / HOLD-while-paying), cancel within a window, join a **waitlist** for full slots,
  and get FIFO notification when a slot frees up. Live STOMP updates turn a slot red the
  moment someone else books it.
- **Managers** run clubs and courts (create/edit/deactivate courts, set weekly opening
  hours, upload a club photo) and see a **dashboard**: revenue, occupancy, busiest hours.
- **Admins** manage everything.
- Security: JWT access (15 min) + rotating hashed refresh tokens (7 days) with **reuse
  detection**; role checks at the endpoint + ownership checks in services (IDOR-proof);
  booking price computed and frozen server-side.

## Architecture in one paragraph

The API talks to Postgres (Flyway-owned schema, `ddl-auto: validate`) and Redis (30s
availability cache + serialization), and publishes `SLOT_BOOKED`/`SLOT_RELEASED` to a STOMP
topic per club after each committed transaction. The Angular app uses **relative URLs**, so
browser and SSR-server talk to *one* origin: `ng serve` proxies `/api` and `/ws` in dev,
and an **nginx gateway** does exactly that in production → **no CORS anywhere**. Production
runs as a Docker stack (nginx → Angular SSR :4000 → API :8080 → Postgres/Redis), images for
both apps in-repo.

## Stack

| Layer | Tech |
|---|---|
| Frontend | Angular 22, signals + `OnPush`, standalone components, SSR (Express), STOMP (`@stomp/stompjs`) |
| API | Spring Boot 4 (Spring MVC, Security, Data JPA, Validation, WebSocket, Actuator), Flyway, JJWT |
| Data | PostgreSQL 17 (exclusion constraint, window functions, partial indexes), Redis 7 |
| Tests | JUnit 5 + Mockito (unit/slice), Failsafe integration tests, Vitest, Playwright e2e |
| CI/CD | GitHub Actions (Java 21/25 matrix, Playwright job, Docker image), Docker Compose, nginx |

## Run it locally

```powershell
# 1) infra (once per session): Postgres (:5433, 5432 is taken by the native service),
#    Redis (:6379), Adminer (:8081)
docker compose up -d

# 2) API on :8080  (Flyway applies migrations at startup)
cd backend
.\mvnw.cmd spring-boot:run

# 3) frontend dev server on :4200 (proxies /api + /ws to :8080)
cd ../frontend
npm ci
npm start
```

Open http://localhost:4200.

**Seeded accounts** (password `secret123`):

| Role | Email |
|---|---|
| ADMIN | `admin@courtside.tn` |
| MANAGER | `manager1@courtside.tn` (Ahmed Ben Ali), `manager2@courtside.tn` (Fatma Trabelsi) |
| MEMBER | `ahmed@test.tn`, `sarah@test.tn`, `karim@test.tn`, `leila@test.tn`, `youssef@test.tn` |

## Tests

```powershell
# backend: 60 unit/slice (Surefire) + 8 integration tests (Failsafe, courtside_test DB)
cd backend; .\mvnw.cmd -B -ntp verify

# frontend: Vitest unit tests
cd frontend; npm test

# end-to-end: Playwright (chromium) against the dev server + API
CI=1 npx playwright test --project=chromium
```

CI runs all of these on every push — see `.github/workflows/ci.yml` and `TESTING.md`.

## Deploy

One command on any VM with Docker:

```powershell
cp .env.example .env    # fill POSTGRES_PASSWORD, JWT_SECRET, SSR_ALLOWED_HOSTS, WEBSOCKET_ORIGINS
docker compose -p courtside-prod -f docker-compose.prod.yml up -d --build
```

nginx + Angular SSR + API + Postgres + Redis, verified end-to-end (bookings, photos,
WebSockets, SSR pages). Full instructions and the env-var contract: **`docs/DEPLOYMENT.md`**.

## API at a glance

| Method | Path | Auth | Returns |
|---|---|---|---|
| POST | `/api/v1/auth/register` · `/login` · `/refresh` · `/logout` | — / refresh token | 201/200 + token pair (rotation, reuse detection) |
| GET | `/api/v1/me` | access | profile |
| GET/POST/PUT/DELETE | `/api/v1/clubs` · `/api/v1/clubs/{id}` | read: none / write: ADMIN or owner | clubs, paginated |
| GET | `/api/v1/clubs/{id}/courts` · GET/PUT/DELETE `/api/v1/courts/{id}` | read: none / write: staff | courts (DELETE = deactivate) |
| GET/PUT/DELETE | `/api/v1/clubs/{id}/opening-hours` | public read / staff write | weekly schedule |
| GET | `/api/v1/courts/{id}/availability?date=` | — | slot grid (Redis-cached 30s) |
| POST | `/api/v1/bookings` · `/hold` · `/bookings/{id}/confirm` | any user | **201, or 409 if the slot is taken**; HOLD auto-releases after 10 min |
| POST/GET/DELETE | `/api/v1/waitlist` · `/api/v1/me/waitlist` · `/api/v1/waitlist/{id}` | any user | FIFO queue for full slots |
| GET/DELETE | `/api/v1/me/bookings` · `/api/v1/bookings/{id}` | owner / staff | paginated bookings, cancel |
| GET | `/api/v1/clubs/{id}/dashboard?from=&to=` | ADMIN / owner | revenue & occupancy analytics |
| GET | `/api/v1/clubs/{id}/photo` · PUT/DELETE | read: none / write: staff | club photo (jpg/png/webp/gif/svg, ≤ 5 MB) |
| WS | `/ws` → `/topic/clubs/{id}/availability` | — | live SLOT_BOOKED / SLOT_RELEASED |

## Project docs

- `docs/PROGRESS.md` — coaching log: review findings, lessons learned, session log
- `docs/DEPLOYMENT.md` — production stack, env vars, verification steps
- `docs/TESTING.md`, `docs/ARCHITECTURE.md`, `docs/best-practices.md`, `docs/instructions.md`
- `docs/session-2-auth-assignment.md` — the original auth assignment