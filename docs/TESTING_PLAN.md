# CourtSide — Testing Plan

Everything in this backend has been verified by hand with PowerShell scripts. That
proves it works *today*, on *my* machine, if I remember to run the right script. Tests
turn that into something a machine re-proves on every push, forever.

This document is the plan and the reasoning; the code follows it.

---

## 1. The testing pyramid, and why the shape matters

```
        ╱╲          few, slow, realistic
       ╱E2E╲         (Playwright — after the frontend exists)
      ╱──────╲
     ╱ INTEGR. ╲     some: real Postgres, real Redis (Testcontainers)
    ╱────────────╲
   ╱  WEB SLICE   ╲  more: Spring MVC + security, services mocked
  ╱────────────────╲
 ╱   UNIT (pure)    ╲ many, milliseconds, no Spring at all
╱────────────────────╲
```

The rule: **push each assertion as low as it can honestly go.** A business rule tested
in a unit test runs in 2 ms; the same rule tested through HTTP against a container runs
in 2 s. Both pass — but only one gets run a hundred times a day without anybody minding.

What each layer is *for*:

| Layer | Answers | Does NOT answer |
|---|---|---|
| Unit | "is my logic right?" | "is it wired correctly?" |
| Web slice | "are status codes, JSON and security right?" | "does the SQL work?" |
| Integration | "does it work against a real database?" | "is the UI right?" |

---

## 2. What we test, concretely

### 2.1 Unit tests — JUnit 5 + Mockito, no Spring context

The fastest wins, on the code that holds the most reasoning:

| Class under test | What we assert |
|---|---|
| `AvailabilityService` | slot grid generation: count, boundaries, back-to-back slots, a booked slot marked unavailable, a **cancelled** booking not blocking, closed day → empty grid, inactive court → empty |
| `BookingService` (rules) | past → 422 · wrong duration → 422 · outside opening hours → 422 · misaligned start → 422 · **price taken from the court, never the request** · cancel window for members vs staff |
| `ClubService.requireCanManage` | ADMIN yes · owning MANAGER yes · **other MANAGER no** · MEMBER no · club with no manager |
| `JwtService` | round-trip (generate → extract) · rejects a tampered payload · rejects a token signed with another key · rejects an expired token |
| `RefreshTokenService` | raw token never equals the stored hash · rotation revokes the old row · **reuse of a revoked token revokes the whole family** · expired token rejected |
| `DashboardService` | cancellation-rate and occupancy arithmetic, including division by zero |

These use **Mockito** to replace repositories: the point is the logic, not the SQL.

### 2.2 Web slice tests — `@WebMvcTest` + MockMvc + spring-security-test

One controller at a time, real Spring MVC + real security filter chain, services mocked.
This is where the **security matrix** becomes executable documentation:

| Test | Expected |
|---|---|
| `POST /clubs` anonymous | 401 |
| `POST /clubs` as MEMBER | 403 |
| `POST /clubs` as MANAGER | 201 |
| `GET /clubs` anonymous | 200 (public) |
| `GET /clubs/{id}/dashboard` anonymous | 401 (not 403 — the rule-order bug we already hit) |
| invalid body | 400 + `fieldErrors` |
| service throws `NotFoundException` | 404 |
| service throws `SlotUnavailableException` | 409 |
| service throws `BusinessRuleException` | 422 |

Why this layer at all? Because `@PreAuthorize`, the filter chain, validation and the
`@ControllerAdvice` are **framework behaviour** — a unit test cannot see them, and an
integration test would be a slow way to check a status code.

### 2.3 Integration tests — `@SpringBootTest` + a real PostgreSQL

> **Implementation note (what actually happened).** The plan was Testcontainers.
> It could not start a container on this machine: its bundled docker-java client
> negotiates an old Docker API version and Engine 29 rejects it — `GET /v1.32/info`
> returns **400** while `GET /info` returns 200. Rather than fight an upstream
> incompatibility, the tests now take the database **from the environment**:
> `docker compose` locally (against a separate `courtside_test` database so a
> truncating test can never wipe development data) and GitHub Actions `services:`
> containers in CI. Both expose the same contract — a JDBC URL — so no test knows
> the difference, and `AbstractIntegrationTest` is the single file to change if
> Testcontainers becomes viable again.

Real PostgreSQL in Docker, real Flyway, real SQL. Reserved for what *only* a real
database can prove:

1. **Migrations apply cleanly** on an empty database (V1 then V2) — this alone catches
   a broken migration before it reaches production.
2. **The exclusion constraint rejects overlaps** — the invariant the whole product rests on.
3. **The concurrency race test** ⭐ — N threads book the same slot simultaneously;
   assert exactly one 201 and exactly one row. This makes permanent the manual test
   that produced "1 success, 9 conflicts".
4. **Cancelled bookings may overlap** — proves the constraint's partial `WHERE` clause.
5. **Native analytics queries run** — window functions, `FILTER`, `generate_series`,
   and the `CAST(... AS date)` fix. Interface projections map correctly (the
   `Instant` vs `OffsetDateTime` bug would have been caught here).
6. **JOIN FETCH queries** return initialised associations — the recurring
   `LazyInitializationException` family, caught once and for all.

Why Testcontainers and not H2? Because H2 has **no exclusion constraints, no
`generate_series`, no `tstzrange`, no `FILTER`**. Testing against a different database
than production would mean testing a different application. Testcontainers starts a
real Postgres in Docker for the test run and throws it away after.

### 2.4 What we deliberately do NOT test

- Getters/setters, Lombok, framework internals — testing the framework is not our job.
- The notification stub's log output.
- Chasing a coverage percentage. Coverage tells you what is *executed*, never what is
  *verified*. We aim for meaningful coverage of services and controllers, and ignore
  the number on DTOs and entities.

---

## 3. Tools

| Tool | Role | Already present? |
|---|---|---|
| JUnit 5 | test framework | ✔ via the `*-test` starters |
| Mockito | replaces collaborators in unit tests | ✔ |
| AssertJ | fluent assertions (`assertThat(x).isEqualTo(y)`) | ✔ |
| MockMvc | drives controllers without a real server | ✔ |
| **spring-security-test** | `@WithMockUser`, authentication in slice tests | to add |
| **Testcontainers** (postgresql, junit-jupiter) | real database in Docker | to add |
| **spring-boot-testcontainers** | `@ServiceConnection` wires the container to Spring automatically | to add |
| JaCoCo | coverage report, later wired into CI | to add |

---

## 4. Conventions we will follow

- **Naming**: `methodUnderTest_condition_expectedResult`
  → `create_whenSlotAlreadyBooked_throwsSlotUnavailable`
  A failing test name should explain the bug without opening the file.
- **Arrange / Act / Assert**, visually separated in every test.
- **One behaviour per test.** Several asserts are fine if they describe one behaviour.
- Test the **contract**, not the implementation: assert what the caller observes, so a
  refactor does not break a hundred tests.
- No conditionals or loops in tests where a parameterised test (`@ParameterizedTest`)
  says it better.
- Shared setup in `@BeforeEach`, never shared mutable state between tests — tests must
  pass in any order, alone or together.

---

## 5. Order of work

1. Add the missing test dependencies (Testcontainers, spring-security-test, JaCoCo).
2. **Unit tests** — `AvailabilityService` first (purest logic, immediate payoff), then
   booking rules, ownership, JWT, refresh tokens.
3. **Web slice tests** — the security matrix and the error-code mapping.
4. **Integration tests** — Testcontainers base, migrations, constraint, and the race test.
5. Run everything, fix what the tests find (they will find something — they always do).
6. Only then: GitHub Actions, which simply runs `mvn verify` on every push.

The last point is the reason to write tests *before* CI: a pipeline that runs nothing
is theatre.
