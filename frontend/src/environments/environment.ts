/**
 * Runtime configuration.
 *
 * Angular swaps this file for environment.prod.ts at build time (see angular.json
 * fileReplacements). The backend does the opposite — it loads app-config.json at
 * RUNTIME so one artifact can be promoted across environments; a browser bundle has
 * no such luxury, which is why the URL is compiled in here.
 */
export const environment = {
  production: false,
  // RELATIVE, like production. Requests go to localhost:4200 and the dev-server proxy
  // (proxy.conf.json) forwards them to :8080. The browser therefore sees a single
  // origin and CORS never applies — instead of loosening the API's CORS policy just
  // to make development work, which is how permissive production configs are born.
  apiUrl: '/api/v1',
  wsUrl: '/ws',
};
