/**
 * Production environment.
 *
 * The API base URL is same-origin by default: in production the SPA is served
 * from behind the same reverse proxy as the API, which removes CORS from the
 * picture entirely. Override it at build time if the two are split.
 */
export const environment = {
  production: true,
  apiUrl: '/api',
  /**
   * How long before expiry the client proactively refreshes the access token.
   * Kept well under the 15 minute token lifetime so a slow request never
   * finishes against an expired token.
   */
  tokenRefreshLeewaySeconds: 60,
};
