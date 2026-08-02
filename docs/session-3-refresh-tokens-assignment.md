# Session 3 — Assignment: Refresh Tokens, Rotation & Logout

**You code it. I review it.** Branch: keep working on `feature/auth`.
The `refresh_tokens` table already exists (Flyway V1) — read it before you start.

## Goal (definition of done)

1. Login & register return **two** tokens: `accessToken` (15 min) + `refreshToken` (7 days)
2. `POST /api/v1/auth/refresh` — exchanges a valid refresh token for a new pair (**rotation**)
3. `POST /api/v1/auth/logout` — revokes the refresh token
4. **Reuse detection**: presenting an already-revoked token revokes ALL that user's tokens
5. Commits along the way (not one giant commit at the end)

---

## Step 1 — RefreshToken entity + repository

Mirror the existing table exactly (`ddl-auto: validate` will punish you otherwise):
`id`, `user` (`@ManyToOne` → User, column `user_id`, **`fetch = FetchType.LAZY`** — think about why),
`tokenHash`, `expiresAt` (`OffsetDateTime`), `revoked` (boolean), `createdAt`.

Repository methods you'll need:
- `Optional<RefreshToken> findByTokenHash(String hash)`
- a way to revoke every token of one user — either `List<RefreshToken> findAllByUserAndRevokedFalse(User user)`
  then loop, or a bulk `@Modifying @Query("update ...")` (bulk needs `@Transactional`; try the
  simple version first and mention the trade-off in your commit message).

## Step 2 — Generating the token (RefreshTokenService)

New service. Two private helpers first:

- **generate the raw token**: `SecureRandom` (NOT `Math.random()`/`Random` — those are predictable;
  `SecureRandom` is cryptographically secure) → 32 bytes → `Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)`.
- **hash it**: `MessageDigest.getInstance("SHA-256")` → digest the UTF-8 bytes → convert to a **hex
  string** (64 chars — matches `VARCHAR(64)` in the schema). `HexFormat.of().formatHex(bytes)`
  (Java 17+) does the conversion in one line.

Public method `issue(User user)`:
1. generate raw token
2. save a row: hash, user, `expiresAt = now + 7 days`, `revoked = false`
3. **return the RAW token** (the caller sends it to the client — the DB only ever holds the hash)

Put the 7 days in `application.yml` next to the JWT secret, injected with `@Value`
(hardcoded durations are a smell — you already did this right for the JWT secret).

## Step 3 — Update the response DTO

`AuthResponse(String accessToken, String refreshToken)` — register and login both return both.
Update `AuthService.register()` and `login()` to call `refreshTokenService.issue(user)`.

## Step 4 — The refresh endpoint (the interesting one)

`POST /api/v1/auth/refresh` with body `{ "refreshToken": "..." }` (new DTO `RefreshRequest`,
`@NotBlank`). In the service:

1. Hash the incoming raw token, look up the row. **Not found → 401.**
2. **Row found but `revoked == true` → REUSE DETECTED**: revoke every refresh token of that user,
   then 401. (Someone is replaying an old token; kill the whole family and force a real login.)
3. `expiresAt` in the past → revoke it, 401.
4. All good → **rotate**: set `revoked = true` on the current row, `issue()` a new one,
   generate a new access token, return both.

Reuse the same `BadCredentialsException` for every failure — the client must not learn *why*.

## Step 5 — Logout

`POST /api/v1/auth/logout`, same body shape. Hash → find → mark revoked. Return **204 No Content**.

Design question to answer in your commit message: *why is the access token still valid for up to
15 minutes after logout, and is that acceptable?*

## Step 6 — Security config

`/api/v1/auth/**` is already `permitAll` — so refresh and logout are reachable without an access
token. **Is that correct?** Think it through: could the user call `/auth/refresh` if their access
token just expired, if the endpoint required a valid access token? Write your reasoning in
PROGRESS.md or the commit message.

## Step 7 — Prove it works

```powershell
# 1. login -> save BOTH tokens
$r = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/login -ContentType "application/json" -Body '{"email":"me@test.tn","password":"secret123"}'
$access = $r.accessToken; $refresh = $r.refreshToken

# 2. refresh -> new pair, both different from the old ones
$r2 = Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/refresh -ContentType "application/json" -Body "{`"refreshToken`":`"$refresh`"}"
$r2.refreshToken -ne $refresh   # must print True

# 3. REUSE the old refresh token -> expect 401 (and all tokens revoked)
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/refresh -ContentType "application/json" -Body "{`"refreshToken`":`"$refresh`"}"

# 4. the NEW token is now dead too (family revoked) -> expect 401
Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/refresh -ContentType "application/json" -Body "{`"refreshToken`":`"$($r2.refreshToken)`"}"

# 5. login again, then logout -> expect 204; refreshing after that -> 401

# 6. In Adminer: SELECT token_hash, revoked, expires_at FROM refresh_tokens;
#    -> hashes are 64 hex chars, NEVER the token you received
```

## Review checklist (what I'll verify)

- [ ] Raw refresh token never stored, never logged — only its SHA-256 hex hash
- [ ] `SecureRandom`, >= 32 bytes of entropy
- [ ] Rotation actually revokes the old row (check the DB, not just the response)
- [ ] Reuse detection revokes the whole family
- [ ] Expiry checked server-side
- [ ] Every failure path → same generic 401
- [ ] Logout returns 204 and revokes
- [ ] Duration configurable, not hardcoded
- [ ] Clean commits with reasoning in the messages

## Bonus (only if the rest is done and green)

A `@Scheduled` job that deletes refresh tokens expired more than 30 days ago
(needs `@EnableScheduling`). Housekeeping: revoked/expired rows accumulate forever otherwise.
