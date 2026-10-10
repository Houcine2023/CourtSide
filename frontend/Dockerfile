# ---------- build stage ----------
# Node and the whole Angular CLI toolchain live only here. The runtime image gets
# the compiled output plus production dependencies, nothing else.
FROM node:22-alpine AS build
WORKDIR /app

# Build descriptor first, so `npm ci` is cached until the lockfile actually changes
# (same reasoning as the backend Dockerfile copying pom.xml before src/).
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Compiles the browser bundle AND the SSR server: dist/frontend/{browser,server}.
RUN npm run build
# Drop devDependencies (Angular CLI, Vitest, Playwright) before they are copied out.
RUN npm prune --omit=dev

# ---------- runtime stage ----------
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=4000

# Never run as root: the SSR process needs no privileges and no writable filesystem.
RUN addgroup -S courtside && adduser -S courtside -G courtside

COPY --from=build --chown=courtside:courtside /app/node_modules ./node_modules
COPY --from=build --chown=courtside:courtside /app/dist/frontend ./

USER courtside
EXPOSE 4000

# Same idea as the backend image: tell the orchestrator the difference between
# "the process started" and "it can serve a request". We probe a static asset
# (index.csr.html) rather than "/": the SSR handler validates the Host header, and
# a loopback probe would be rejected by that SSRF protection even when healthy.
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/index.csr.html" >/dev/null 2>&1 || exit 1

# node is PID 1 (exec form), so it receives SIGTERM directly and shuts down cleanly.
CMD ["node", "server/server.mjs"]
