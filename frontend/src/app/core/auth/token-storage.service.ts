import { Injectable } from '@angular/core';

import { AuthenticatedUser } from './auth.models';

/**
 * Where the session lives in the browser.
 *
 * Deliberate split:
 *
 * - the **access token stays in memory only**. It is the credential that opens
 *   every endpoint, and keeping it out of persistent storage limits what an XSS
 *   payload can walk away with to a 15 minute window.
 * - the **refresh token is persisted**, so closing the tab does not sign the
 *   user out. It is revocable server-side, which is what makes that acceptable:
 *   a stolen refresh token can be killed from the user administration screen,
 *   and it rotates on every use.
 *
 * A cleaner design would put the refresh token in an HttpOnly, SameSite cookie.
 * That needs the API and the SPA on one origin plus CSRF handling, which is a
 * deployment decision this release does not force. The trade-off is recorded in
 * the README.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private static readonly REFRESH_TOKEN_KEY = 'jewellery-erp.refresh-token';
  private static readonly USER_KEY = 'jewellery-erp.user';

  private accessToken: string | null = null;
  private accessTokenExpiresAt: number | null = null;

  getAccessToken(): string | null {
    return this.accessToken;
  }

  setAccessToken(token: string, expiresInSeconds: number): void {
    this.accessToken = token;
    this.accessTokenExpiresAt = Date.now() + expiresInSeconds * 1000;
  }

  /** True when there is no access token, or it is within `leeway` of expiring. */
  isAccessTokenExpiring(leewaySeconds: number): boolean {
    if (!this.accessToken || this.accessTokenExpiresAt === null) {
      return true;
    }
    return Date.now() >= this.accessTokenExpiresAt - leewaySeconds * 1000;
  }

  getRefreshToken(): string | null {
    return this.read(TokenStorageService.REFRESH_TOKEN_KEY);
  }

  setRefreshToken(token: string): void {
    this.write(TokenStorageService.REFRESH_TOKEN_KEY, token);
  }

  /**
   * The cached user, so a page reload can render the shell immediately instead
   * of flashing the login screen while the token is refreshed. It is a cache,
   * not an authority: the permissions that matter are re-read from the server.
   */
  getUser(): AuthenticatedUser | null {
    const raw = this.read(TokenStorageService.USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthenticatedUser;
    } catch {
      this.clear();
      return null;
    }
  }

  setUser(user: AuthenticatedUser): void {
    this.write(TokenStorageService.USER_KEY, JSON.stringify(user));
  }

  clear(): void {
    this.accessToken = null;
    this.accessTokenExpiresAt = null;
    this.remove(TokenStorageService.REFRESH_TOKEN_KEY);
    this.remove(TokenStorageService.USER_KEY);
  }

  /* Storage can throw in a private window or when site data is blocked, so every
   * access is guarded and degrades to "no stored session" rather than crashing
   * the application. */
  private read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private write(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* Session simply will not survive a reload. */
    }
  }

  private remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      /* Nothing to do. */
    }
  }
}
