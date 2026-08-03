# CourtSide — Architecture & Design Decisions

A reference for the backend as it stands: how it is layered, how authentication
works, how caching works, and every optimisation/best practice applied, with the
reasoning behind each. Written to be defended in an interview, not just read.

---

## 1. The big picture

```
HTTP request
   │
   ▼
[ Security filter chain ]  JwtAuthenticationFilter → SecurityContext
   │                        (401 if the endpoint needs auth and nobody is identified)
   ▼
[ Controller ]  HTTP only: parse, @Valid, @PreAuthorize (role), map to DTO, status code
   │
   ▼
[ Service ]     business rules, ownership checks, @Transactional boundaries
   │
   ▼
[ Repository ]  Spring Data JPA / native SQL for analytics
   │
   ▼
[ PostgreSQL ]  the final authority: constraints the application cannot bypass
        +
   [ Redis ]    cache of computed availability
        +
   [ STOMP ]    push notifications after commit
```

**The rule that shapes everything:** each layer has one job, and the database is the
last line of defence, not the first. Validation happens in the DTO, business rules in
the service, and *invariants* in the schema — because only the schema is immune to a
bug in another code path or a race between two requests.

### Package layout

| Package | Contains | Never contains |
|---|---|---|
| `controllers` | HTTP mapping, status codes, `@PreAuthorize` | business logic |
| `services` | rules, transactions, ownership | HTTP types, `HttpServletRequest` |
| `repositories` | queries only | logic |
| `entities` | JPA mapping, mirrors Flyway exactly | behaviour |
| `dtos` | API contract in/out (records) | entities leaking outward |
| `security` | filter chain, JWT filter | business rules |
| `exceptions` | typed exceptions + one `@ControllerAdvice` | scattered try/catch |

---

## 2. Authentication: access token + refresh token

### 2.1 Why two tokens at all

A JWT is **stateless**: the server stores nothing, it just verifies a signature. That
is what lets any instance serve any request without shared session memory. The price:
**a JWT cannot be un-issued.** It is valid until `exp`, full stop.

- Long-lived access token → a stolen token means a long-lived compromise.
- Short-lived access token → users would re-login every 15 minutes.

The two-token model resolves the conflict:

| | Access token | Refresh token |
|---|---|---|
| Format | JWT (signed, self-describing) | 32 random bytes, Base64url |
| Lifetime | **15 min** (`JwtService.jwtExpiration`) | **7 days** (`app.refresh.expiration-days`) |
| Sent | with every API call | only to `/auth/refresh` and `/auth/logout` |
| Server state | none | one row in `refresh_tokens`, **hashed** |
| Revocable | ✗ | ✓ (that is what logout does) |

You accept statelessness in 15-minute windows, and keep control at the boundaries.

### 2.2 The access token in detail

Created in `JwtService.generateToken()`:

```
subject = email        (who)
claim   role           (what they may do — read by the filter to build authorities)
iat / exp              (issued at / expires 15 min later)
signature = HMAC-SHA(header.payload, secret)
```

Three points worth defending:

1. **Signed, not encrypted.** Anyone can Base64-decode the payload and read it. That
   is why it holds an email and a role — never a password, never anything private.
   The signature guarantees *integrity*, not confidentiality: change `"role":"ADMIN"`
   and the signature no longer matches, so the token is rejected.
2. **The secret never lives in git.** `application.yml` reads
   `${JWT_SECRET:dev-default}` — the environment variable wins in production, the
   inline default only makes local dev frictionless. HMAC needs ≥ 32 bytes.
3. **Verification is stateless.** `JwtAuthenticationFilter` parses the header, validates
   the signature and expiry, loads the user, and puts an `Authentication` in the
   `SecurityContext`. No database session, no lookup table. On a bad token it does
   **not** throw — it logs at debug and continues unauthenticated, so Spring Security
   answers a clean 401 instead of a 500.

### 2.3 The refresh token in detail

`RefreshTokenService`:

- **Generation**: `SecureRandom`, 32 bytes = 256 bits of entropy, Base64url-encoded.
  Never `Random`/`Math.random()` — those are predictable from a few outputs.
- **Storage**: only `SHA-256(token)` as 64 hex chars reaches the database. A dumped
  `refresh_tokens` table is worthless to an attacker.
- **Why SHA-256 and not BCrypt?** BCrypt is deliberately *slow* to make brute-forcing
  guessable human passwords impractical. A 256-bit random string cannot be guessed at
  all, so slowness would only add latency to every refresh. Fast hash + huge entropy
  is the correct pairing; slow hash + low entropy is the password case.
- **Why not a JWT?** Because it is checked against the database anyway. A JWT's value
  is avoiding a lookup — useless here, and it would leak claims for nothing.

### 2.4 Rotation and reuse detection (the part that impresses)

Every call to `/auth/refresh` **consumes** the presented token (marks it revoked) and
issues a brand-new pair. A refresh token is therefore single-use.

That single-use property enables the trap:

```
token presented → hash → look it up
   not found            → 401
   found but REVOKED    → REUSE DETECTED: revoke every live token of that user → 401
   expired              → revoke it → 401
   valid                → revoke it, issue a new pair → 200
```

If an already-used token reappears, either an attacker stole it or the legitimate user
replayed an old one. We cannot tell which, so we assume the worst and **kill the whole
family** — thief and victim both have to log in again. That is the industry-standard
response (OAuth 2 BCP), and it is the reason revoked rows are *kept* rather than
deleted: a deleted row is indistinguishable from a token that never existed.

One transactional subtlety that is easy to get wrong: Spring rolls back on any
`RuntimeException` by default, so throwing the 401 right after revoking would **undo
the revocation**. Hence:

```java
@Transactional(noRollbackFor = BadCredentialsException.class)
```

Commit the security action, then let the exception fly.

### 2.5 Deliberate limitations (say these before an interviewer finds them)

- **Logout does not kill the access token** — it cannot; a JWT is not revocable. The
  window is ≤ 15 minutes. Closing it entirely would require a denylist in Redis, which
  reintroduces the state we removed. Accepted trade-off, documented.
- Refresh tokens are not bound to a device or IP; a stolen one works anywhere until
  used, which is exactly why reuse detection exists.
- No lockout after N failed logins yet (rate limiting is the next hardening step).

---

## 3. Redis cache

### 3.1 What is cached, and why only that

Only `AvailabilityService.getForDay(courtId, date)` — the availability grid.

It is the right (and only) candidate because it is:
- **read-heavy**: every visitor browsing a club hits it, repeatedly;
- **expensive-ish**: a query plus slot generation over the whole day;
- **identical for everyone**: the grid does not depend on who is asking, so one cached
  value serves all users (a per-user cache would be nearly useless).

Nothing on the write path is cached. Nothing user-specific is cached.

### 3.2 The correctness argument (the important part)

> A stale availability grid **cannot** cause a double booking.

Because the booking path never trusts the cache: `BookingService.create()` re-queries
overlapping bookings, and above all the PostgreSQL exclusion constraint decides. The
worst a stale grid can do is show a slot as free for a few seconds; the user clicks,
gets a clean 409, and the grid refreshes.

**Generalised rule: never cache anything whose staleness could corrupt data.** Cache
what is merely *displayed*; re-verify anything that is *acted upon*.

### 3.3 How it is wired

```java
@Cacheable(value = "availability", key = "#courtId + ':' + #date")
public AvailabilityResponse getForDay(Long courtId, LocalDate date)
```

- **Key** = `availability::{courtId}:{date}` — it contains *every input that changes
  the result*. A key that omits an input is the classic cache bug: two different
  questions sharing one answer.
- **TTL 30 s** — a safety net, not the main mechanism. Even if an eviction were ever
  missed, the wrong answer disappears in half a minute.
- **JSON serialisation**, not Java serialisation: readable in `redis-cli`,
  language-agnostic, survives class renames. Jackson needs `JavaTimeModule` for
  `OffsetDateTime`, and type info to rebuild records — with a
  `PolymorphicTypeValidator` restricting deserialisation to our own packages, because
  accepting arbitrary class names from a cache is a known RCE vector.
- `disableCachingNullValues()` — a null result is not an answer worth remembering.

### 3.4 Eviction: the half that people forget

TTL alone would mean up to 30 s of lying after every booking. So writes evict:

```java
private void evictAvailability(Court court, OffsetDateTime start) {
    cacheManager.getCache("availability")
                .evict(court.getId() + ":" + localDate);   // one court, one day
}
```

Called on **create** and on **cancel**. Two design points:

- **Precise, not global.** Evicting one key keeps every other court's grid warm.
  `FLUSHALL` on every booking would make the cache pointless.
- Done in code rather than with `@CacheEvict`, because the key needs the *local* date,
  which only exists after converting the stored instant into the club's zone — not
  something to express readably in a SpEL string.

Measured: **341 ms cold → 32 ms warm**, entry gone immediately after a booking.

### 3.5 Known limitation

Redis being down currently fails the request (2 s timeout). A production hardening
step is a `CacheErrorHandler` that logs and falls through to the database — a cache
should degrade, never break.

---

## 4. Optimisations & best practices applied

### 4.1 Database

| Practice | Where | Why |
|---|---|---|
| **Exclusion constraint** `EXCLUDE USING gist (court_id WITH =, tstzrange(...) WITH &&)` | `V1__init_schema.sql` | Makes overlapping bookings *impossible*, even under perfect concurrency. Application checks cannot be atomic across transactions. |
| Partial constraint (`WHERE status IN ('HOLD','CONFIRMED')`) | same | Cancelled bookings may overlap freely → history kept, slot reusable. |
| Flyway migrations, `ddl-auto: validate` | `application.yml` | Schema is versioned code; Hibernate may never invent it. Entities are validated against reality at startup. |
| Explicit indexes on FKs and query paths | `V1` | PostgreSQL does **not** auto-index foreign keys. |
| `CHECK` constraints (times, enums, ranges) | `V1` | Last line of defence, independent of application code. |
| `NUMERIC`/`BigDecimal` for money | `Court`, `Booking` | Binary floats cannot represent 0.10; cents drift. |
| `TIMESTAMPTZ` for instants, `TIME` for wall-clock | bookings vs opening hours | A booking is a moment; "we open at 08:00" is a local rule. |
| Half-open ranges everywhere (`start < end2 && end > start1`) | Java **and** SQL | Back-to-back slots must not collide, and both definitions must agree. |
| Price frozen on the booking row | `Booking.price` | A later price change must not rewrite history. Correct denormalisation. |

### 4.2 JPA / persistence

| Practice | Why |
|---|---|
| `open-in-view: false` | OSIV hides lazy-loading bugs behind late queries. Off means problems surface in development. |
| `@ManyToOne(fetch = LAZY)` everywhere | `@ManyToOne` is EAGER by default — the seed of N+1. |
| `JOIN FETCH` variants (`findByIdWithClubAndManager`, `findByIdWithManager`, `findByIdDetailed`) | Load exactly what the DTO will touch, in **one** query. The cure for both N+1 and `LazyInitializationException`. |
| Fetch-join only single-valued relations alongside `Pageable` | Fetch-joining a *collection* forces in-memory pagination. |
| Dirty checking instead of `save()` inside a transaction | Managed entities flush automatically; an explicit save is noise. |
| `saveAndFlush` in `BookingService.create` | Forces the INSERT *now* so the constraint violation is catchable → clean 409 instead of a 500 at commit. |
| `@Transactional(readOnly = true)` on queries | No dirty-check snapshots; routable to replicas later. |
| `@Version` optimistic locking on `Court` and `Booking` | Concurrent edits raise `ObjectOptimisticLockingFailureException` → 409, no silently lost update. |
| Interface projections for analytics | No entity, no persistence context — the right tool for read-only reporting. |
| Query-only repository `extends Repository<T,ID>` | `DashboardRepository` exposes no `save()`/`deleteAll()`. Least privilege for APIs. |
| No NULL parameters in JPQL | An untyped NULL makes Postgres guess `bytea` → *"function lower(bytea) does not exist"*. Normalise in the service. |

### 4.3 Security

| Practice | Why |
|---|---|
| BCrypt for passwords, SHA-256 for random tokens | Slow-and-salted for guessable secrets; fast for high-entropy ones. |
| Generic 401 on every auth failure | Never reveal whether the email or the password was wrong (account enumeration). |
| Two-layer authorization: `@PreAuthorize` (role) + service ownership checks | Roles answer "what kind of user?", ownership answers "is this *yours*?". Roles alone let any manager edit any club. |
| Ownership check on the dashboard | Blocks **IDOR**: changing the club id in the URL cannot reveal a competitor's revenue. |
| `STATELESS` sessions, CSRF disabled *with a reason* | A Bearer header is not auto-sent by the browser, so classic CSRF does not apply. |
| `AuthenticationEntryPoint` → 401 | Without it Spring Security answers a blanket 403 and the status code lies. |
| Specific security rules before general ones | First match wins; the dashboard GET was being swallowed by public `/clubs/**`. |
| Server-side price and user | Never trust a client-sent amount, never let anyone book "as" someone else. |
| Secrets via environment variables | Nothing sensitive in git; user-secrets/env in production. |
| Polymorphic-type validator on the cache mapper | Deserialising arbitrary class names is an RCE vector. |
| Idempotent, silent logout | Erroring on an unknown token turns logout into an oracle for testing stolen tokens. |
| No private data in WebSocket events | The topic is public: it says "this slot changed", nothing else. |

### 4.4 API design

- **Consistent error contract** — one `ErrorResponse` shape everywhere, `fieldErrors`
  only for validation (`@JsonInclude(NON_NULL)`).
- **Honest status codes**: 400 malformed · 401 unidentified · 403 identified-but-not-allowed ·
  404 missing · **409** state conflict (slot taken, concurrent edit) · **422** understood
  but refused by a business rule.
- **Pagination with a hard cap** (`Math.min(size, 100)`) — an uncapped `size` is a DoS vector.
- **Stable `PageResponse`** instead of returning Spring's `Page` — the JSON contract is
  ours, not a framework internal.
- DTOs in and out; entities never cross the controller boundary (privacy + decoupling).
- **Soft delete** where history matters (courts deactivate, bookings cancel).
- PUT for the idempotent opening-hours upsert; DELETE returns 204.
- Nested paths for ownership (`/clubs/{id}/courts`), flat paths for items (`/courts/{id}`).
- Reporting ranges capped at 366 days.

### 4.5 Concurrency & correctness

1. **Defence in depth on booking**: friendly pre-check in Java → exclusion constraint
   in Postgres. Neither replaces the other.
2. **Optimistic locking** on entities edited by several people.
3. **Publish after commit** for WebSocket events — announcing inside the transaction
   would broadcast a booking a rollback then erases.
4. **Notifications never break the transaction**: broadcast failures are logged, not
   propagated. The booking already succeeded.
5. Verified empirically: 10 simultaneous requests → 1 × 201, 9 × 409, exactly 1 row.

### 4.6 Operations

- `docker-compose` with **healthchecks** — dependants wait for *ready*, not *started*.
- Actuator health endpoint (public), used by the run scripts and later by CI/K8s probes.
- All durations and zones configurable (`app.timezone`, `app.booking.cancel-hours-before`,
  `app.refresh.expiration-days`) — no magic numbers buried in code.
- Scheduled cleanup of long-expired refresh tokens (`@EnableScheduling` — the annotation
  is inert without it).
- One topic per club for broadcasts; fine granularity keeps push cheap.

### 4.7 The "silent no-op" family — annotations that do nothing until enabled

| Annotation | Requires | Symptom if forgotten |
|---|---|---|
| `@PreAuthorize` | `@EnableMethodSecurity` | every endpoint open to any authenticated user |
| `@Scheduled` | `@EnableScheduling` | the job never runs, no warning |
| `@Cacheable` | `@EnableCaching` | cache silently bypassed |
| `@Transactional` | proxy call (not `this.method()`) | no transaction, no rollback |

---

## 5. Holds, waitlist and scheduled work

**HOLD flow** — reserve first, charge second. `POST /bookings/hold` creates a booking
with status `HOLD` and a `hold_expires_at` deadline. The exclusion constraint already
treats HOLD as occupying, so the slot is genuinely blocked. `POST /bookings/{id}/confirm`
promotes it to CONFIRMED; a job releases it otherwise. Confirmation validates the
*timestamp*, not whether the job has run — never let a scheduler define correctness.

**Waitlist** — FIFO by `created_at`, which is the only ordering users perceive as fair.
Being notified does **not** reserve the slot: announcing to everyone is simple and never
leaves a slot locked by someone who walked away. Entries are deactivated rather than
deleted, and the unique index only guards *active* rows so a user can re-join later.
Both cancellation and hold expiry trigger notification, inside the same transaction —
if the cancellation rolls back, nobody is told about a slot that is still taken.

**Scheduled jobs** (`BookingJobs`, all in one class so "what runs at 3am?" is answerable):
release expired holds (`fixedDelay`, never `fixedRate` — that can overlap itself),
hourly reminders whose idempotency comes from the *window* rather than a flag, and a
nightly waitlist cleanup. Each job catches its own exceptions, because a `@Scheduled`
method that throws may never be rescheduled.

**Notifications** go through one `NotificationService` that currently logs. The
abstraction is the point: swapping in SMTP touches one file.

---

## 6. Honest gaps (what a reviewer will ask about)

- **No automated tests yet** — every behaviour above was verified by hand. This is the
  next task, and the 10-way race test is the first thing to make permanent.
- Single-instance WebSocket broker: a second node would not see the first node's events
  (fix: RabbitMQ relay or Redis pub/sub).
- Cache does not degrade gracefully if Redis is down.
- No rate limiting / login lockout.
- Payment is simulated: `/confirm` trusts the caller instead of a payment provider's
  webhook. The state machine is real; the money is not.
