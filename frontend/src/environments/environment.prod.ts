export const environment = {
  production: true,
  // Same-origin in production: the API is served behind the same host/reverse proxy,
  // which removes CORS from the picture entirely.
  apiUrl: '/api/v1',
  wsUrl: '/ws',
};
