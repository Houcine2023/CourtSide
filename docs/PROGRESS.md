# CourtSide — Progress & Coaching Log

Living document. Updated at every session and every code review.
Rule: a review finding stays **OPEN** until verified fixed in the code — not just claimed.

---

## Review findings tracker

### Session 2 review #1 — auth code (2026-07-10)

| # | Severity | Finding | File | Status |
|---|---|---|---|---|
| 1 | 🔴 | `existByEmail` → must be `existsByEmail` (Spring Data name grammar; app crashes at startup) | `UserRepository` | ✅ FIXED (verified 07-11) |
| 2a | 🔴 | `password_hash` mapped `nullable = true` but schema says NOT NULL — entity must mirror the migration | `User` | ✅ FIXED (verified 07-11) |
| 2b | 🔴 | `full_name` mapped `unique = true` — constraint invented, doesn't exist in schema; also missing `nullable = false` | `User` | ✅ FIXED (verified 07-11) |
| 3a | 🟡 | email missing `@Email` | `RegisterRequest` | ✅ FIXED (verified 07-11) |
| 3b | 🟡 | password missing `@NotBlank` — `@Size` **accepts null** (the null-skip trap of Bean Validation) | `RegisterRequest` | ✅ FIXED (verified 07-11) |
| 3c | 🟡 | `fullName` has no validation — add `@NotBlank` | `RegisterRequest` | ✅ FIXED (verified 07-11) |
| 4 | 🟡 | `LoginResponse` is an empty duplicate — **file still exists, delete it** | `dtos` | **OPEN** |
| 5 | 🟢 | `long id` → `Long` (wrapper): unsaved entity should have `null` id, not `0` | `User` | ✅ FIXED (verified 07-11) |
| 6 | 🟢 | `@Repository` redundant on a `JpaRepository` interface | `UserRepository` | **OPEN** |
| — | 💭 | Design smell: `@Size(min=8)` on **login** password — policy belongs to registration | `LoginRequest` | ✅ FIXED (verified 07-11) |

**Review #1 score: 8 / 10 fixed.**

### Session 2 review #2 — full auth run (2026-07-11)

Behavior tests: register 201 ✅ · duplicate 409 ✅ · login 200 ✅ · validation 400 ✅ · BCrypt in DB ✅
· wrong password → **403 (should be 401)** ❌ · no token → **403 (should be 401)** ❌ · valid token + unknown URL → **403 (should be 404)** ❌

| # | Severity | Finding | File | Status |
|---|---|---|---|---|
| N1 | 🔴 | Login failure threw bare `RuntimeException` → now `BadCredentialsException`, handler alive | `AuthService` | ✅ FIXED (by Claude, verified: test 4 → 401) |
| N2 | 🔴 | No `AuthenticationEntryPoint` → added `HttpStatusEntryPoint(UNAUTHORIZED)` | `SecurityConfig` | ✅ FIXED (verified: test 6 → 401) |
| N3 | 🟡 | ERROR dispatch blocked → added `dispatcherTypeMatchers(ERROR).permitAll()` | `SecurityConfig` | ✅ FIXED (verified: test 8 → 404) |
| N4 | 🟡 | Empty `catch {}` in filter → now logs rejection reason at debug | `JwtAuthenticationFilter` | ✅ FIXED |
| N5 | 🟡 | `${JWT_SECRET}` no default → `${JWT_SECRET:dev-default}` + UTF-8 charset pinned | `application.yml`, `JwtService` | ✅ FIXED |
| N6 | 🟡 | `GET /api/v1/me` implemented (`MeController`, `MeResponse`) | controllers | ✅ FIXED (verified: 200 with token, 401 without) |
| N7 | 🟢 | Naming: `token`, `jwtExpiration`, `csrf`, `getSigningKey` | several | ✅ FIXED |
| N8 | 🟢 | Error contract unified: `ErrorResponse` + optional `fieldErrors` (+`@JsonInclude(NON_NULL)`) | `GlobalExceptionHandler`, `ErrorResponse` | ✅ FIXED |
| N9 | 🟢 | Whole `User` entity used as the principal — a slim principal is cleaner; revisit when adding roles/RBAC | `JwtAuthenticationFilter` | note |
| P1 | 🔴 process | Zero commits → user's work committed as-is (`181082c`), fixes in separate commits so the diff is readable | git | ✅ FIXED |

**Review #2 verification (2026-07-11): all 8 behavior tests green** — register 201, duplicate 409,
login 200, wrong password **401**, validation 400, no token **401**, /me 200, unknown URL **404**.
Also fixed during the session: findings #4 (LoginResponse deleted) and #6 (@Repository removed) from review #1.
Bonus lesson: PowerShell 5.1 `Set-Content -Encoding utf8` writes a **BOM** (`﻿`) that javac rejects —
"illegal character" on line 1 means invisible bytes, not visible code.

---

## Comprehension questions (can be quizzed anytime)

1. Why is the `HttpSecurity` config written with lambdas (`csrf -> csrf.disable()`)? *(hint: Builder pattern)*
2. Why must `anyRequest().authenticated()` be the **last** rule?
3. Why does the JWT filter go **before** `UsernamePasswordAuthenticationFilter`?
4. Why does login return the same generic 401 whether the email or the password was wrong?
5. Why do we store a *hash* of refresh tokens instead of the token itself?
6. A JWT is signed, not encrypted — what does that mean for what you may put in the payload?
7. Why can't a server "cancel" an access token, and how do refresh tokens work around it?
8. Why is the refresh token a random string instead of a JWT?
9. Why SHA-256 for refresh tokens but BCrypt for passwords?
10. What is token rotation, and what does reuse detection protect against?

---

## Lessons learned (one line each — review weekly)

### Session 1 — infra & schema (2026-07-09)
- **Docker Compose** = dev environment as code; containers reachable by service name inside the Docker network (`postgres`), by `localhost:PORT` from the host.
- **Healthcheck** = "ready", not just "started" — CI waits on it too.
- **Flyway**: numbered immutable SQL migrations; history in `flyway_schema_history`; schema = code in git.
- **`ddl-auto: validate`**: Hibernate never creates schema, only checks entities match it.
- **`open-in-view: false`**: OSIV hides lazy-loading bugs; keep it off.
- **Constraint** = a data rule the DB itself enforces; last line of defense (NOT NULL, UNIQUE, PK, FK, CHECK, EXCLUDE).
- **`EXCLUDE USING gist (court_id WITH =, tstzrange(...) WITH &&)`** = race-proof double-booking prevention; Java checks alone cannot stop two simultaneous requests — the DB referee can.
- Postgres does **not** auto-index foreign keys.

### Session 2 — auth (in progress)
- Spring Security = a **filter-chain corridor** in front of controllers; secure by default (401 everywhere) the moment the dependency lands.
- CSRF protection defends cookie-based auth; a Bearer header is never auto-sent → disable for stateless APIs (and know why).
- `STATELESS` sessions = every request proves itself via the token.
- Authorization rules are evaluated **top-down, first match wins** → specific `permitAll` first, catch-all last.
- Spring Data derives queries from a **method-name grammar** (`existsBy`, `findBy`, `countBy`...).
- Bean Validation trap: most annotations (`@Size`, `@Email`) **treat null as valid** — mandatory fields also need `@NotBlank`/`@NotNull`.
- The entity must **mirror the migration exactly** — never invent or relax constraints in annotations.
- JJWT split (api/impl/jackson + runtime scope) = program against a stable API, implementation swappable — same idea as JPA vs Hibernate.
- Starters inherit versions from the Spring Boot parent POM; third-party deps (jjwt) pin their own.

---

### From review #2 (2026-07-11)
- **401 vs 403**: 401 = "I don't know who you are" (missing/bad credentials); 403 = "I know you, and you may not do this."
- Spring Security with **no configured AuthenticationEntryPoint** falls back to `Http403ForbiddenEntryPoint` → everything unauthenticated gets 403. Configure the entry point to get honest 401s.
- An uncaught exception in a controller **bounces to `/error`** (a second, internal dispatch). If your security rules block the ERROR dispatch, the real status (404/500) is masked. `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`.
- An `@ExceptionHandler` only fires for exceptions that actually get thrown — writing the handler without throwing the exception = dead code. Trace the whole path.
- jjwt picks the HMAC algorithm from the key length (32 bytes → HS256, 48 → HS384, 64 → HS512) when you don't specify it.
- Empty `catch {}` = destroying the evidence. At minimum, log at debug level.
- Commit as you go, in logical chunks — a day of uncommitted work is a day you can lose.

### Session 3 — implementation (2026-08-02)
- `@ManyToOne` is **EAGER by default** — always set `fetch = LAZY`, then load explicitly with `JOIN FETCH` when needed (the N+1 cure).
- **Spring rolls back on any RuntimeException by default.** Throwing `BadCredentialsException` after revoking tokens would silently undo the revocation → `@Transactional(noRollbackFor = BadCredentialsException.class)`.
- Inside a transaction, a managed entity's setter is enough — Hibernate **dirty checking** flushes it; no `save()` call needed.
- `SecureRandom` ≠ `Random`: the latter is predictable from a few outputs.
- Revoked rows are **kept, not deleted** — that's what makes reuse detection possible; a scheduled job prunes them after 30 days.
- `@Scheduled` does nothing without **`@EnableScheduling`** on the app class (silent no-op trap).
- Logout is **idempotent** and silent on unknown tokens — erroring would turn it into an oracle for testing stolen tokens.
- **Port conflict debugging**: a native `postgresql-x64-17` Windows service owned 5432, so the app silently reached the WRONG database ("password authentication failed"). Container republished on **5433**. Lesson: when credentials "suddenly" fail, verify *which* server you're actually talking to (`Get-NetTCPConnection -LocalPort`).

### Session 4 — clubs & courts CRUD + RBAC (2026-08-02)
- **Entity naming rules**: no `@Table` → Hibernate derives the table from the CLASS name (`RefreshToken` → `refresh_token`, singular) → startup validation fails. A `@ManyToOne` field must be named after the OBJECT (`user`), not the column (`user_id`): Lombok generates `getUser()` and JPQL navigates `rt.user`; the column name belongs in `@JoinColumn`.
- `@CreationTimestamp` missing → NULL into a NOT NULL column: a runtime failure the compiler cannot see.
- **NULL parameters in JPQL are poison**: `:q is null or ...` makes Postgres receive an untyped NULL → *"function lower(bytea) does not exist"*. Normalise in the service (`null → "%"`, `null → ""`) instead.
- **LazyInitializationException**: mapping an entity to a DTO *after* the transaction closes (OSIV off) explodes on lazy relations. Cure: `left join fetch` in the query. Safe with `Pageable` for `@ManyToOne` (single-valued); a fetch-joined COLLECTION would force in-memory paging.
- **Two layers of authorization**: `@PreAuthorize("hasAnyRole(...)")` answers "what kind of user are you?" at the endpoint; the service answers "is this YOUR object?" (ownership). Roles alone would let any manager edit any club.
- `@EnableMethodSecurity` is required or `@PreAuthorize` is silently ignored — same trap family as `@EnableScheduling`.
- **Absent field ≠ null value**: treating a missing `managerId` as "set to null" made every admin edit unassign the club's manager. The test suite caught it; PATCH-style semantics need explicit "was it provided?" handling.
- Money is `BigDecimal`/NUMERIC, never double. Enums are `EnumType.STRING`, never ORDINAL (reordering the enum would rewrite history).
- **Soft delete** for courts (`active = false`): the FK cascades to bookings, so a real DELETE would erase booking history.
- `@Version` optimistic locking → concurrent edits raise `ObjectOptimisticLockingFailureException` → mapped to **409**, so no change is silently lost.
- Cap `size` on paginated endpoints — an uncapped `size=1000000` is a DoS vector.

### Session 5 — availability & conflict-safe booking (2026-08-02) ⭐ the centrepiece
- **The overlap test must be identical in Java and in the DB**: `[a1,a2)` and `[b1,b2)` overlap iff `a1 < b2 && a2 > b1`. Strict inequalities = half-open ranges = back-to-back slots (09:30–11:00 then 11:00–12:30) don't collide, exactly like Postgres `tstzrange`. If the two definitions disagree, Java offers a slot the DB then rejects.
- **Two layers against double booking**: the Java pre-check gives a friendly 409; the exclusion constraint is what makes cheating *impossible*. Neither replaces the other — application checks can't be atomic across transactions.
- **`saveAndFlush`, not `save`**: forces the INSERT now so the constraint violation is catchable inside the try block. With `save()` the flush happens at commit, outside the method, and the user gets a raw 500 instead of a 409.
- Verified with **10 truly simultaneous requests**: 1 × 201, 9 × 409, exactly 1 row in the DB.
- **Status codes have meanings**: 400 = malformed payload · 422 = understood but breaks a business rule · 409 = the state of the world says no (and might succeed later).
- Price is **computed server-side** and frozen on the row — never trust a client-sent amount, and never let a later price change rewrite past bookings.
- Availability = ONE query for the whole day, then match slots in memory (a query per slot is a textbook N+1).
- Wall-clock (`LocalTime`, opening hours) vs instants (`TIMESTAMPTZ`, bookings): a zone converts between them — `app.timezone`.
- `LazyInitializationException` struck again on `court.getClub()` in the response DTO → fetch-join variant `getByIdWithClub`. Recurring rule: **anything the DTO touches must be loaded inside the transaction**.
- Cancelling = status change, not deletion: the constraint ignores CANCELLED rows, so the slot frees up while the history survives (proved in the DB: a CANCELLED and a CONFIRMED row share the same start time).
- PowerShell gotcha for testing: `Start-Job` dies with the shell; JSON dates come back as **strings**, not DateTime.

### Session 6 — dashboard (window functions) & Redis cache (2026-08-03)
- **`SUM(SUM(x)) OVER ()`** — empty OVER() = the grand total on every row, the idiomatic way to compute a percentage of a total without a subquery. Verified: 57.1% + 42.9% = 100%.
- **`SUM(SUM(x)) OVER (ORDER BY week)`** = running total. Window functions run AFTER GROUP BY, which is why they can take an aggregate as input.
- **`COUNT(*) FILTER (WHERE ...)`** — several different aggregates over the same rows in one pass (cleaner than `COUNT(CASE WHEN ...)`).
- `LEFT JOIN` in reporting keeps zero-activity rows visible (a court nobody books is exactly what a manager needs to see); `NULLIF` guards division by zero.
- `AT TIME ZONE` before `EXTRACT(HOUR ...)`, or a 20:00 booking in Tunis is reported as 19:00 UTC.
- **Interface projections** map native-query columns to getters — but do NO type conversion: `date_trunc` returns TIMESTAMPTZ → JDBC gives `Instant`, so declaring `OffsetDateTime` fails at runtime. Declare what the driver actually returns and convert in the service.
- Query-only repositories can `extend Repository<T,ID>` instead of `JpaRepository` — no `save()`, no `deleteAll()`. Least privilege applies to APIs too.
- **Rule order bit again**: the dashboard is a GET under `/clubs/**` (public), so it was swallowed by the permitAll rule and anonymous callers got 403 from `@PreAuthorize` instead of 401. Specific rules must precede general ones.
- Ownership check on the dashboard blocks **IDOR** — a manager changing the club id in the URL cannot read a competitor's revenue.
- Always cap a reporting date range, or one request scans the whole table.
- **Caching**: `@Cacheable` needs `@EnableCaching` (silent-no-op family again). The key must contain every input that changes the result. Cache JSON (readable, survives refactors) not Java serialisation, and restrict polymorphic typing to your own packages — deserialising arbitrary class names is an RCE vector.
- Correctness argument for caching availability: a stale grid is harmless because the booking path re-checks and the DB constraint has the final word. **Never cache anything whose staleness could corrupt data.**
- Evict precisely (one court + one day), not the whole cache. Measured: 341ms cold → 32ms warm, entry evicted on every booking/cancellation.

### Session 7 — WebSocket live availability (2026-08-03)
- Raw WebSocket is a byte pipe with no notion of topics; **STOMP** adds SUBSCRIBE/SEND semantics — that is why a broadcast feature uses it. SockJS adds transport fallback when a proxy blocks WebSocket.
- **Publish AFTER commit**, never inside the transaction: a rollback would leave subscribers greying out a slot that is actually free, with nothing to correct them. `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()`.
- A failed broadcast must never fail the booking that already committed — catch and log; the client sees the truth on its next refresh.
- **One topic per club** (`/topic/clubs/{id}/availability`): fine-grained topics keep broadcast cheap. A client watching one club is not woken by every booking in the country.
- The event payload carries **no private data** (no user, no price, no booking id) because anyone may subscribe — only "this slot changed".
- The in-memory `SimpleBroker` works for one instance; with several nodes a client on node A never sees an event from node B → external relay (RabbitMQ) or Redis pub/sub.
- Server is the only publisher: no client-to-server destinations are accepted, which removes a whole class of abuse.

### Session 8 — holds, waitlist, occupancy, scheduled jobs (2026-08-03) — backend 100%
- **V2 migration**: the first schema evolution. Never touch an applied migration — every environment already ran V1 and Flyway compares checksums. Changes always go forward.
- **Partial indexes**: `CREATE INDEX ... WHERE status = 'HOLD'` indexes only the handful of rows the job scans. Same trick for the unique index on *active* waitlist entries, which lets a user re-join a slot after leaving.
- **HOLD flow** = reserve first, charge second. The exclusion constraint already counts HOLD as occupying, so a hold really blocks the slot. Confirming checks *time*, not whether the job has run — never let a scheduler define correctness.
- **`generate_series`** materialises a calendar as rows — the only way to count capacity for days that have no bookings (a plain JOIN would lose them). `EXTRACT(ISODOW)` matches our 1–7 `day_of_week`.
- Occupancy = confirmed ÷ capacity, where capacity is *derived* (opening hours × courts × days), never stored. Verified: 4 days × 9 slots = 36 → 1 booking = 2.8%.
- **`::date` vs `:param`**: Hibernate parses `:fromDate::date` as a parameter literally named `fromDate::date`. Use `CAST(x AS date)` in native queries.
- **`fixedDelay` vs `fixedRate`**: fixedRate can overlap itself if a run is slow; fixedDelay waits after completion. The safe default for DB jobs.
- A `@Scheduled` method that throws may never be rescheduled — always catch inside the job.
- Reminder idempotency from the **window** (`[t, t+1h)` run hourly) rather than a "reminded" flag: simpler schema, at the cost of a missed run meaning a missed reminder.
- Notifications go through **one abstraction** (`NotificationService`) that currently logs — swapping in SMTP later touches one file. Dependency inversion where it actually pays.
- Waitlist design: notifying does **not** reserve. Announcing to everyone is fair and never leaves a slot locked by someone who walked away.
- **Lombok cascade lesson**: one real compile error (a duplicate field) makes annotation processing fail, so *every* generated getter/setter appears missing. Dozens of "cannot find symbol getX" usually means ONE genuine error — fix it and the rest evaporate.

### Session 9 — the test suite & CI (2026-08-04) — 57 tests green
- **The pyramid is about speed, not purity**: push every assertion as low as it honestly goes. Unit tests run in ~0.3s; the same rule through HTTP + a real database takes 30s. Both pass — only one gets run all day.
- A **web slice** (`@WebMvcTest`) exists because `@PreAuthorize`, the filter chain, bean validation and `@ControllerAdvice` are *framework* behaviour that a unit test cannot see and an integration test is too slow to check.
- `@WithMockUser` puts a **String** principal in the context, so `@AuthenticationPrincipal User` resolves to null. Authenticate slice tests with `authentication(...)` and the real principal type instead.
- **A test drove a production fix**: the slice failed with "no HttpSecurity bean" because auto-configuration does not run in a slice → added `@EnableWebSecurity`, making `SecurityConfig` self-contained. Good tests improve the code, not just check it.
- **Surefire runs `*Test`, Failsafe runs `*IT`.** Naming a class `...IT` and expecting `mvn test` to run it means it silently never runs — the worst possible failure mode for a test. Failsafe also splits `integration-test` from `verify` so teardown/reporting still happen after a failure.
- Spring Boot 4 moved slice annotations into per-technology modules: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.
- **Testcontainers vs Docker Engine 29**: docker-java requests `/v1.32/info` → 400. Diagnosed by talking to the named pipe by hand (`/info` → 200, `/v1.32/info` → 400). Fallback: the environment provides the database (compose locally, `services:` in CI) — same contract, one file to change back.
- Also found on this machine: two literal garbage entries (`%PATH:` and `%`) in the Windows PATH, which crashed Testcontainers' executable lookup with `InvalidPathException`.
- Integration tests use a **separate `courtside_test` database** + `flyway.clean-disabled: false`: every run re-proves the migrations from scratch and cannot touch development data.
- **Schedulers off in tests** (cron `0 0 5 31 2 *` = 31 February, never): a background job firing mid-assertion is the classic flaky-suite cause.
- The **race test is now code**: 10 threads released together by a `CountDownLatch` start gate (without it the first commits before the last starts, and the race never happens) → exactly 1 success, 9 conflicts, 1 row.
- CI: matrix on Java 21 + 25, service containers **with health checks** (otherwise the job races the database), `~/.m2` cached, reports uploaded with `if: always()` — you need them most when the build is red.
- Dockerfile: multi-stage (JDK only in the build stage), non-root user, `MaxRAMPercentage` because a JVM otherwise sizes its heap from the *host's* memory and gets OOM-killed, and `exec` form so the JVM is PID 1 and receives SIGTERM.

## Session log

| Date | Session | Done |
|---|---|---|
| 2026-07-09 | 1 — Infra | Repo + compose (Postgres/Redis/Adminer) + Spring Boot skeleton + `application.yml` + Flyway V1 with exclusion constraint; verified live (health UP, overlap rejected). Commit `4d9f3aa` on `main`. |
| 2026-08-03 | 8 — Holds, waitlist, occupancy | V2 migration (hold_expires_at, waitlist restructure, partial indexes). HOLD → confirm → auto-release job; waitlist join/leave/list with fair FIFO notification on cancellation AND hold expiry; occupancy rate via `generate_series` capacity; notification stub; 3 scheduled jobs. Verified: HOLD blocks the slot (409), auto-release after expiry, waitlist notified on both paths, duplicate join 422, occupancy 1/36 = 2.8%. Fixed `::date` vs `:param` parser clash. **Backend 100% of spec.** |
| 2026-08-03 | 7 — WebSocket | STOMP over SockJS at `/ws`, `AvailabilityEvent` broadcast per club after commit. Verified with a real STOMP client: SLOT_BOOKED on booking, SLOT_RELEASED on cancel. **Backend feature-complete.** |
| 2026-08-03 | 6 — Dashboard & Cache | Window-function analytics (running total, share of total, FILTER aggregates, busiest hours in local time) with interface projections; Redis cache on availability (30s TTL, precise eviction, 341ms→32ms). Fixed: Instant vs OffsetDateTime in projections, dashboard 403→401 rule ordering. |
| 2026-08-02 | 5 — Availability & Booking ⭐ | Opening hours (upsert PUT), availability grid, **conflict-safe booking**, cancel, my-bookings. Business rules: past, duration, opening hours, grid alignment, cancel window. New: `BusinessRuleException`→422, `SlotUnavailableException`→409. 16/16 behaviour tests + **concurrency race test: 10 simultaneous requests → 1 success, 9 conflicts, 1 DB row**. Bug found & fixed: LazyInitializationException on `court.getClub()` in the response DTO. |
| 2026-08-02 | 4 — Clubs & Courts + RBAC | He rewrote `RefreshToken` (broke 7 things: missing `@Table`, `user_id` field name, `long` id, no `@CreationTimestamp`, no `@Column` metadata) → fixed with explanations; `.vscode/settings.json` added so the Java extension imports the Maven project in `backend/`. Built: `Club`/`Court`/`Sport` entities, repositories with fetch joins, DTOs (+`PageResponse`), `NotFoundException`, 403/404/409 handlers, `ClubService`/`CourtService` with ownership rules, `ClubController`/`CourtController` with `@PreAuthorize`, public GETs. **Verification caught 3 real bugs** (untyped NULL in JPQL, LazyInitializationException on DTO mapping, admin edit wiping the manager) — all fixed, 18/18 tests green. |
| 2026-08-02 | 3 — Refresh tokens | He wrote the repository (correct) + an empty entity file. Claude completed the session with teaching comments: `RefreshToken` entity, JOIN FETCH query, `RefreshTokenService` (SecureRandom + SHA-256 hex, issue/consume/revoke/revokeAllForUser), rotation + **reuse detection**, `/auth/refresh` + `/auth/logout` (204), `AuthResponse` pair, configurable TTL, `@Scheduled` cleanup job. Fixed a **port conflict** (native postgres on 5432 → container moved to 5433). All 7 behavior tests green; DB holds only 64-char hashes. |
| 2026-07-10 | 2 — Auth (ongoing) | Assignment issued (`session-2-auth-assignment.md`). He wrote: `User`, `Role`, `UserRepository`, DTOs, `SecurityConfig` (PasswordEncoder done). Review #1 delivered (10 findings above). SecurityFilterChain explained line-by-line — he implements next, then `JwtService` → filter → service/controller → manual tests. |

### Session 3 — refresh tokens (theory, 2026-07-11)
- JWT = `header.payload.signature`, Base64 — **signed, not encrypted**: anyone can read the payload (jwt.io), only the secret holder can forge a valid signature. Never put secrets in claims.
- Signature = tamper detection: edit the payload → signature no longer matches → rejected.
- Sessions = wristband (server remembers, needs shared memory across instances); JWT = passport (carries identity, any instance verifies with the secret) → enables `STATELESS`.
- The cost of statelessness: **a JWT cannot be revoked** — valid until `exp`. Short life = safer but annoying; long life = convenient but dangerous if stolen.
- Two-token model: short access JWT (15 min, stateless) + long opaque refresh token (7 days, stored hashed in DB → revocable). Control at the boundaries.
- Refresh token is NOT a JWT: it's checked in the DB anyway, so self-description buys nothing.
- SHA-256 (fast) for a 256-bit random token vs BCrypt (deliberately slow) for guessable human passwords.
- **Rotation**: every refresh issues a new refresh token and revokes the old one → enables **reuse detection** (an already-used token reappearing = theft signal → revoke the whole family).

## Next milestones

- [x] Session 2 — auth: register/login/JWT/`/me`, all 8 behavior tests green (commits `181082c` + `46b83e4`)
- [x] Session 3 — refresh tokens, rotation, reuse detection, logout, cleanup job (all 7 tests green)
- [x] Week 2 — clubs/courts CRUD + RBAC + error contract (18/18 behavior tests green)
- [ ] **First automated tests** (JUnit + Mockito + Testcontainers) — still zero; every check so far has been manual
- [ ] CI skeleton (GitHub Actions) + push repo to GitHub
- [x] Week 3 — availability grid + conflict-safe booking (16/16 tests + 10-way race test green)
- [x] Manager dashboard (window functions + occupancy), Redis cache, WebSocket live updates
- [x] HOLD/confirm payment flow, waitlist, scheduled jobs (release, reminders, cleanup)
- [x] **BACKEND 100% COMPLETE against `FLAGSHIP_PROJECT_SPEC.md`** (MVP + V1; stretch items QR/i18n/photos intentionally out of scope)
- [x] **Automated tests: 57 green** (49 unit/slice via Surefire + 8 integration via Failsafe) + JaCoCo
- [x] **GitHub Actions CI**: Java 21/25 matrix, Postgres+Redis services, cached deps, artifacts, Docker image job
- [ ] Next: push the repo to GitHub (no remote yet) → Angular frontend → deploy
- [ ] Hardening backlog: rate limiting, cache degradation if Redis is down, multi-node WebSocket broker
