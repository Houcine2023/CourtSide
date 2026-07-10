# Session 2 — Assignment: Register & Login with JWT

**You write the code. No copy-pasting from tutorials — use the official docs and your brain.
When done (or stuck > 30 min on one step), ask for review.**

## Goal (definition of done)

1. `POST /api/v1/auth/register` — creates a user, returns a JWT
2. `POST /api/v1/auth/login` — verifies credentials, returns a JWT
3. `GET /api/v1/me` — protected: returns the current user's info with a valid token, `401` without
4. Everything committed on a branch called `feature/auth` (not on main — practice the workflow)

Refresh tokens & logout are **session 3** — don't build them yet, even though the table exists.

---

## Step 0 — Add the dependencies (and watch what breaks)

In `pom.xml` add:
- `spring-boot-starter-security`
- the JJWT library, 3 artifacts, version `0.12.6`:
  - `io.jsonwebtoken:jjwt-api`
  - `io.jsonwebtoken:jjwt-impl` (scope `runtime`)
  - `io.jsonwebtoken:jjwt-jackson` (scope `runtime`)

**Experiment before coding:** start the app and call `/actuator/health`.
It now returns 401. Understand why: Spring Security auto-configures itself and, by default,
locks EVERY endpoint. Your whole job in this session is to relax that default *deliberately*.

## Step 1 — The User entity + repository

- `@Entity` class `User` mapped to the **existing** `users` table (Flyway made it;
  `ddl-auto: validate` will check your mapping matches — a wrong column type/name will
  crash startup with a clear error; that's the feature working, read the error).
- Fields: `id`, `email`, `passwordHash`, `fullName`, `role` (make a `Role` enum:
  `MEMBER, MANAGER, ADMIN`, stored as string), `createdAt`.
- Hint: Hibernate converts camelCase to snake_case automatically (`passwordHash` -> `password_hash`).
- Careful: `User` is a reserved-ish word — the table is named `users`, so `@Table(name = "users")`.
- `UserRepository extends JpaRepository<User, Long>` with:
  `Optional<User> findByEmail(String email)` and `boolean existsByEmail(String email)`.

## Step 2 — The DTOs (never expose the entity)

Java `record`s are perfect here:
- `RegisterRequest(email, password, fullName)` — with validation annotations:
  `@Email`, `@NotBlank`, password `@Size(min = 8)`.
- `LoginRequest(email, password)`
- `AuthResponse(accessToken)`
- `MeResponse(email, fullName, role)`

## Step 3 — SecurityConfig

A `@Configuration` class with two beans:
1. `PasswordEncoder` -> `BCryptPasswordEncoder`. (Why BCrypt: it's *slow on purpose* and
   salted — brute-forcing a leaked hash becomes impractical.)
2. `SecurityFilterChain` that:
   - disables CSRF (stateless token API — know why: CSRF attacks ride on auto-sent cookies;
     a Bearer header is not auto-sent)
   - sets session management to `STATELESS`
   - permits `/api/v1/auth/**` and `/actuator/health`, requires authentication for the rest
   - registers your JWT filter (step 5) before `UsernamePasswordAuthenticationFilter`

## Step 4 — JwtService

One class, two jobs:
- `generateToken(User)` -> a signed JWT: subject = email, a `role` claim,
  issued-at now, expiry **15 minutes**, HS256 signature.
- `extractEmail(token)` (parse + validate signature/expiry; let it throw on bad tokens).

The signing secret goes in `application.yml` (e.g. `app.jwt.secret`) — HS256 needs
**at least 32 bytes**. Generate a long random string. Add a comment: real deployments
inject this via environment variable.

## Step 5 — JwtAuthFilter

Extends `OncePerRequestFilter`:
1. Read the `Authorization` header; no header or no `Bearer ` prefix -> just continue the chain
2. Extract the token, validate it via JwtService
3. Load the user, build a `UsernamePasswordAuthenticationToken` (with authorities from the
   role, format `ROLE_MEMBER`), put it in `SecurityContextHolder`
4. Invalid/expired token -> do NOT crash; continue unauthenticated (Security will 401 later)

## Step 6 — AuthService + AuthController

- `register`: email already exists -> throw (handle as **409**); otherwise hash the password
  (`encoder.encode`), save, return `AuthResponse` with a fresh token.
- `login`: find by email, check `encoder.matches(raw, hash)`; ANY failure -> same generic
  **401 "invalid credentials"** (never reveal whether email or password was wrong — why?).
- Controller: `@Valid` on request bodies; register returns **201**.
- Bonus points: a small `@ControllerAdvice` that turns validation errors into a clean 400 JSON
  and your conflict exception into 409.

## Step 7 — The protected endpoint

`GET /api/v1/me` -> reads the authenticated principal from the SecurityContext
(hint: inject `Authentication` as a controller method parameter) -> returns `MeResponse`.

## Step 8 — Prove it works (manual tests, save the commands)

```powershell
# 1. register -> expect 201 + token
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/register -ContentType "application/json" -Body '{"email":"me@test.tn","password":"secret123","fullName":"Houcine"}'
# 2. duplicate register -> expect 409
# 3. login -> expect 200 + token
# 4. /me WITHOUT token -> expect 401
Invoke-WebRequest http://localhost:8080/api/v1/me
# 5. /me WITH token -> expect 200 + your info
Invoke-RestMethod http://localhost:8080/api/v1/me -Headers @{ Authorization = "Bearer <paste token>" }
# 6. check the DB: SELECT email, password_hash FROM users; -> the hash must NOT be your password
```

## Review checklist (what I will look for)

- [ ] Raw password never stored, never logged, never returned
- [ ] Secret >= 32 bytes, not hard-coded in Java
- [ ] Login failure is one generic 401 for both wrong email and wrong password
- [ ] Filter never throws on a bad token (graceful 401, not 500)
- [ ] DTOs used everywhere — the `User` entity never leaves the service layer
- [ ] Validation annotations actually enforced (`@Valid` present)
- [ ] Correct status codes: 201 / 200 / 400 / 401 / 409
- [ ] Work is on `feature/auth` with clear commits

## Allowed help

Official Spring Security docs, jjwt README on GitHub, your own EduDash code.
**Not allowed:** copy-pasting a full tutorial project. If you're stuck, ask me a *specific*
question ("my filter runs but SecurityContext is empty in the controller — why?") — that's
what a senior colleague is for.
