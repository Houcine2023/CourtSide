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

## Session log

| Date | Session | Done |
|---|---|---|
| 2026-07-09 | 1 — Infra | Repo + compose (Postgres/Redis/Adminer) + Spring Boot skeleton + `application.yml` + Flyway V1 with exclusion constraint; verified live (health UP, overlap rejected). Commit `4d9f3aa` on `main`. |
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
- [ ] Week 3 — availability grid + conflict-safe booking + the concurrency race test
