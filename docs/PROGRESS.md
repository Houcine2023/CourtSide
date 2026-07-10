# CourtSide — Progress & Coaching Log

Living document. Updated at every session and every code review.
Rule: a review finding stays **OPEN** until verified fixed in the code — not just claimed.

---

## Review findings tracker

### Session 2 review #1 — auth code (2026-07-10)

| # | Severity | Finding | File | Status |
|---|---|---|---|---|
| 1 | 🔴 | `existByEmail` → must be `existsByEmail` (Spring Data name grammar; app crashes at startup) | `UserRepository` | **OPEN** |
| 2a | 🔴 | `password_hash` mapped `nullable = true` but schema says NOT NULL — entity must mirror the migration | `User` | **OPEN** |
| 2b | 🔴 | `full_name` mapped `unique = true` — constraint invented, doesn't exist in schema; also missing `nullable = false` | `User` | **OPEN** |
| 3a | 🟡 | email missing `@Email` | `RegisterRequest` | **OPEN** |
| 3b | 🟡 | password missing `@NotBlank` — `@Size` **accepts null** (the null-skip trap of Bean Validation) | `RegisterRequest` | **OPEN** |
| 3c | 🟡 | `fullName` has no validation — add `@NotBlank` | `RegisterRequest` | **OPEN** |
| 4 | 🟡 | `LoginResponse` is an empty duplicate — delete, return `AuthResponse` from login too | `dtos` | **OPEN** |
| 5 | 🟢 | `long id` → `Long` (wrapper): unsaved entity should have `null` id, not `0` | `User` | **OPEN** |
| 6 | 🟢 | `@Repository` redundant on a `JpaRepository` interface | `UserRepository` | **OPEN** |
| — | 💭 | Design smell: `@Size(min=8)` on **login** password — policy belongs to registration | `LoginRequest` | **OPEN** |

---

## Comprehension questions (can be quizzed anytime)

1. Why is the `HttpSecurity` config written with lambdas (`csrf -> csrf.disable()`)? *(hint: Builder pattern)*
2. Why must `anyRequest().authenticated()` be the **last** rule?
3. Why does the JWT filter go **before** `UsernamePasswordAuthenticationFilter`?
4. Why does login return the same generic 401 whether the email or the password was wrong?
5. Why do we store a *hash* of refresh tokens instead of the token itself?

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

## Session log

| Date | Session | Done |
|---|---|---|
| 2026-07-09 | 1 — Infra | Repo + compose (Postgres/Redis/Adminer) + Spring Boot skeleton + `application.yml` + Flyway V1 with exclusion constraint; verified live (health UP, overlap rejected). Commit `4d9f3aa` on `main`. |
| 2026-07-10 | 2 — Auth (ongoing) | Assignment issued (`session-2-auth-assignment.md`). He wrote: `User`, `Role`, `UserRepository`, DTOs, `SecurityConfig` (PasswordEncoder done). Review #1 delivered (10 findings above). SecurityFilterChain explained line-by-line — he implements next, then `JwtService` → filter → service/controller → manual tests. |

## Next milestones

- [ ] Finish session 2: fixes + filter chain + JwtService + JwtAuthFilter + AuthService/Controller + `/api/v1/me` + manual tests → full review ("review my auth")
- [ ] Session 3: refresh tokens + logout
- [ ] CI skeleton (GitHub Actions) + push repo to GitHub
- [ ] Week 2 per `FLAGSHIP_PROJECT_SPEC.md`: clubs/courts CRUD + RBAC + error contract + first tests
