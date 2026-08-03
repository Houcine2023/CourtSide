# CourtSide 🎾

Sports-facility booking platform — Angular · Spring Boot 3 · PostgreSQL · Redis.
Flagship project: conflict-safe bookings enforced by a PostgreSQL exclusion constraint.

## Run it locally

### 1. Start the infrastructure (once per work session)

```powershell
cd CourtSide
docker compose up -d        # starts postgres + redis + adminer
docker compose ps           # check: courtside-db must be "healthy"
```

- **Adminer** (browse the database): http://localhost:8081
  — system `PostgreSQL`, server `postgres`, user/pass/db `courtside`
- Note: the container publishes Postgres on **host port 5433** (5432 is taken by the
  native `postgresql-x64-17` Windows service). Inside Docker it is still 5432.
- Stop everything at the end: `docker compose down` (data survives in the volume;
  `docker compose down -v` wipes it)

### 2. Start the backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Wait for the Spring banner + "Started CourtsideApiApplication". Flyway applies any new
migrations automatically at startup. Stop with `Ctrl+C`.

- Health check: http://localhost:8080/actuator/health -> `{"status":"UP"}`
- Dev JWT secret has a built-in default; production overrides it via the `JWT_SECRET`
  environment variable.

### 3. Test the API (PowerShell)

```powershell
# register (expect 201 + token)
$r = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/register `
  -ContentType "application/json" `
  -Body '{"email":"me@test.tn","password":"secret123","fullName":"Houcine"}'

# login (expect 200 + token)
$r = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/login `
  -ContentType "application/json" -Body '{"email":"me@test.tn","password":"secret123"}'

# who am I? (expect 200 with your info)
Invoke-RestMethod http://localhost:8080/api/v1/me `
  -Headers @{ Authorization = "Bearer $($r.accessToken)" }

# without token (expect 401)
Invoke-RestMethod http://localhost:8080/api/v1/me

# refresh: exchange the refresh token for a NEW pair (the old one dies — rotation)
$new = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/refresh `
  -ContentType "application/json" -Body "{`"refreshToken`":`"$($r.refreshToken)`"}"

# replay the OLD refresh token -> 401 AND every session of that user is revoked
# (reuse detection: a token used twice means someone stole it)
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/refresh `
  -ContentType "application/json" -Body "{`"refreshToken`":`"$($r.refreshToken)`"}"

# logout (expect 204) — revokes the refresh token
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/logout `
  -ContentType "application/json" -Body "{`"refreshToken`":`"$($new.refreshToken)`"}"
```

## API

| Method | Path | Auth | Returns |
|---|---|---|---|
| POST | `/api/v1/auth/register` | — | 201 + token pair |
| POST | `/api/v1/auth/login` | — | 200 + token pair |
| POST | `/api/v1/auth/refresh` | refresh token | 200 + **new** token pair (rotation) |
| POST | `/api/v1/auth/logout` | refresh token | 204 |
| GET | `/api/v1/me` | access token | 200 + your profile |
| GET | `/api/v1/clubs?q=&city=&page=&size=` | — | paginated clubs |
| POST/PUT/DELETE | `/api/v1/clubs/{id}` | ADMIN / owning MANAGER | club CRUD |
| GET | `/api/v1/clubs/{id}/courts` | — | courts of a club |
| POST | `/api/v1/clubs/{id}/courts` · PUT/DELETE `/api/v1/courts/{id}` | ADMIN / owning MANAGER | court CRUD (DELETE = deactivate) |
| GET/PUT/DELETE | `/api/v1/clubs/{id}/opening-hours` | public read / staff write | weekly schedule |
| GET | `/api/v1/courts/{id}/availability?date=` | — | slot grid (Redis-cached 30s) |
| POST | `/api/v1/bookings` | any user | **201, or 409 if the slot is taken** |
| GET | `/api/v1/me/bookings?upcomingOnly=` | any user | paginated bookings |
| DELETE | `/api/v1/bookings/{id}` | owner / club staff | cancel (status → CANCELLED) |
| GET | `/api/v1/clubs/{id}/dashboard?from=&to=` | ADMIN / owning MANAGER | revenue & occupancy analytics |
| WS | `/ws` → subscribe `/topic/clubs/{id}/availability` | — | live SLOT_BOOKED / SLOT_RELEASED |

Access token = JWT, 15 min. Refresh token = opaque random string, 7 days, stored **hashed**.

## Project docs

- `FLAGSHIP_PROJECT_SPEC.md` (in Portfilo) — full feature spec & milestones
- `docs/PROGRESS.md` — coaching log: review findings, lessons, session log
- `docs/session-2-auth-assignment.md` — the auth assignment
