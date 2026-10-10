# Deployment

CourtSide ships as a Docker stack: nginx gateway in front, then the Angular SSR
server, the Spring Boot API, PostgreSQL and Redis, all private on one network.
Postgres data is the only thing that lives on disk (a named volume).

## Option A — any VM with Docker (recommended for a single host)

Prerequisites: Docker Engine + Docker Compose v2.

```bash
git clone https://github.com/Houcine2023/CourtSide.git
cd CourtSide

cp .env.example .env         # then edit:
                            #   POSTGRES_PASSWORD  a real password
                            #   JWT_SECRET         at least 32 bytes
                            #   SSR_ALLOWED_HOSTS  the public hostname(s)
                            #   WEBSOCKET_ORIGINS  the site origin (http/https)
                            #   HTTP_PORT          80 unless 80 is taken

docker compose -p courtside-prod -f docker-compose.prod.yml up -d --build
```

> **Why `-p courtside-prod`**: a distinct project name keeps the production stack entirely
> separate from the local dev stack (`docker compose up -d`), which uses the same default
> project name and the same `postgres`/`redis` service names. Without it, running `docker
> compose up -f docker-compose.prod.yml` on a machine that already ran the dev stack would
> **recreate the dev containers** from the prod spec — un-mapping Postgres from `:5433` and
> swapping its volume.

Open `http://<host>` (or `:$HTTP_PORT`). The first start builds both images and
runs the Flyway migrations; give the backend ~60s.

- Tail logs: `docker compose -p courtside-prod -f docker-compose.prod.yml logs -f`
- Redeploy after a pull: `docker compose -p courtside-prod -f docker-compose.prod.yml up -d --build`
- Stop but keep data: `... down`  |  Wipe everything: `... down -v`

### Verification from outside

```bash
curl -s http://<host>/api/v1/clubs?size=1          # JSON list of clubs
curl -sI http://<host>/api/v1/clubs/1/photo        # 200, image/svg+xml
curl -s http://<host>/api/v1/clubs/1/dashboard     # 401 (auth required)
open http://<host>                                  # bookable site
```

## Environment variables

All go through `.env`; nothing secret lives in git.

| Variable | Required | Purpose |
|---|---|---|
| `POSTGRES_PASSWORD` | yes | Password for the `POSTGRES_USER` role. Set at first volume init. |
| `JWT_SECRET` | yes | HMAC key for access tokens — **≥ 32 bytes**. Generate: `openssl rand -base64 48`. |
| `SSR_ALLOWED_HOSTS` | yes | Comma-separated public hostname(s). Angular's SSR rejects Host headers not on this list (SSRF protection), and the browser's Host arrives verbatim through nginx. Fail-closed: if wrong, pages return 400. |
| `WEBSOCKET_ORIGINS` | yes | Comma-separated origin(s) allowed to open `/ws`. Must match the site origin, e.g. `https://courtside.example.com`. |
| `HTTP_PORT` | no | Host port for the gateway (default 80). |
| `POSTGRES_DB` / `POSTGRES_USER` | no | Defaults to `courtside`. |

The backend also reads `SPRING_*` env vars (Spring Boot relaxed binding), e.g.
`SPRING_DATA_REDIS_HOST`, `APP_BOOKING_HOLD_MINUTES` — the compose file wires the
defaults so most deployments never touch these.

## How the pieces talk

```
Browser
  │  http(s)://host  /api/v1/..., /ws, everything else
  ▼
nginx gateway (the ONLY published port)
  ├── /api/*   → backend  :8080   (Spring Boot, port 8080)
  ├── /ws      → backend  :8080   (raw WebSocket, Upgrade headers added)
  └── /*       → frontend :4000   (Angular Express SSR)
```

The browser always talks to a single origin, so there is **no CORS** anywhere: the
frontend builds relative URLs and nginx fans out. `WEBSOCKET_ORIGINS` and
`SSR_ALLOWED_HOSTS` replace the CORS widowing that a multi-origin setup usually
tolerates — the deployed host is explicitly allowlisted instead.

## Option B — managed database (Render / Railway / Fly.io)

Same images, different backing stores. Provide a Postgres URL and a Redis URL as
env vars instead of running the containers:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/courtside`
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST` (+ `_PORT`)
- `JWT_SECRET`, `SSR_ALLOWED_HOSTS`, `WEBSOCKET_ORIGINS` as above

Deploy `backend/` (repo runner or Dockerfile → `java $JAVA_OPTS -jar app.jar`,
port 8080) and `frontend/` (Dockerfile → `node server/server.mjs`, port 4000)
as two services, then let the platform's router handle `/api` and `/ws` routing
(use the same upgrade headers as `deploy/nginx.conf`).

## Notes & caveats

- **Safe to run only with TLS in front.** The nginx config listens on HTTP/80.
  Terminate TLS at the platform or with a Let's Encrypt companion container; keep
  the JWT and cookies on the same secure origin.
- **Single instance.** The STOMP broker is in-memory and the exit-constraint
  booking is per-database. Horizontal scaling needs a STOMP relay and sticky
  sessions — out of scope for this project, documented in `WebSocketConfig`.
- **Seeding.** Flyway migrations create the schema and seed 5 clubs with SVG
  placeholder photos; `POSTGRES_PASSWORD` change after first start does not
  re-seed — recreate the volume (`down -v`) or ALTER the role yourself.
- **`SSR_ALLOWED_HOSTS` is fail-closed.** Until it lists the real hostname, the
  site answers HTTP 400 for page requests. This is intentional (SSR SSRF guard).