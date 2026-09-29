import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, of, shareReplay, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SUPPRESS_ERROR_TOAST } from '../interceptors/error.interceptor';
import { AuthenticatedUser, LoginRequest, LoginResponse } from './auth.models';
import { PermissionCode } from './permissions';
import { TokenStorageService } from './token-storage.service';

/**
 * Session state and the operations that change it.
 *
 * The signed-in user is exposed as a signal, so guards, the sidebar and the
 * permission directive all read one source of truth and update together when a
 * refresh brings back a changed permission set.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly storage = inject(TokenStorageService);

  private readonly currentUser = signal<AuthenticatedUser | null>(this.storage.getUser());

  /** Single in-flight refresh, shared by every request that hits a 401 at once. */
  private refreshInFlight$: Observable<LoginResponse> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly permissions = computed(() => new Set(this.currentUser()?.permissions ?? []));
  readonly isAdministrator = computed(() => this.currentUser()?.roles.includes('ROLE_ADMIN') ?? false);
  readonly mustChangePassword = computed(() => this.currentUser()?.mustChangePassword ?? false);

  /**
   * True when the signed-in user holds the permission.
   *
   * Used to hide controls. It is never the only thing standing between a user
   * and an action - the matching `@PreAuthorize` on the server is.
   */
  has(permission: PermissionCode | string): boolean {
    return this.permissions().has(permission);
  }

  hasAny(permissions: readonly (PermissionCode | string)[]): boolean {
    return permissions.some((permission) => this.has(permission));
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, credentials, {
        // The login form renders the failure inline; a toast on top would repeat it.
        context: new HttpContext().set(SUPPRESS_ERROR_TOAST, true),
      })
      .pipe(tap((response) => this.applySession(response)));
  }

  /**
   * Exchanges the stored refresh token for a new token pair.
   *
   * Concurrent callers share one request: a screen that fires five list calls on
   * load would otherwise trigger five refreshes, four of which would fail
   * because the token rotates on first use.
   */
  refresh(): Observable<LoginResponse> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    const refreshToken = this.storage.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available.'));
    }

    this.refreshInFlight$ = this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(
        tap({
          next: (response) => {
            this.applySession(response);
            this.refreshInFlight$ = null;
          },
          error: () => {
            this.refreshInFlight$ = null;
            this.clearSession();
          },
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

    return this.refreshInFlight$;
  }

  /** True when a refresh is worth attempting (there is a token and it may be stale). */
  shouldRefresh(): boolean {
    return (
      this.storage.getRefreshToken() !== null &&
      this.storage.isAccessTokenExpiring(environment.tokenRefreshLeewaySeconds)
    );
  }

  hasAccessToken(): boolean {
    return this.storage.getAccessToken() !== null;
  }

  /** Re-reads the current user, picking up a permission change made by an admin. */
  loadCurrentUser(): Observable<AuthenticatedUser | null> {
    return this.http.get<AuthenticatedUser>(`${environment.apiUrl}/auth/me`).pipe(
      tap((user) => {
        this.currentUser.set(user);
        this.storage.setUser(user);
      }),
      catchError(() => of(null)),
    );
  }

  /**
   * Ends the session.
   *
   * The server call revokes the refresh token; the local state is cleared
   * regardless of whether that call succeeds, because a user who pressed
   * "sign out" must end up signed out even if the network is down.
   */
  logout(redirectUrl?: string): void {
    const refreshToken = this.storage.getRefreshToken();
    if (refreshToken) {
      this.http
        .post<void>(`${environment.apiUrl}/auth/logout`, { refreshToken })
        .pipe(catchError(() => of(void 0)))
        .subscribe();
    }
    this.clearSession();
    void this.router.navigate(['/login'], {
      queryParams: redirectUrl ? { redirect: redirectUrl } : undefined,
    });
  }

  /** Clears local session state without navigating. Used by the error interceptor. */
  clearSession(): void {
    this.storage.clear();
    this.currentUser.set(null);
  }

  /** Called after a self-service password change, which clears the forced flag. */
  markPasswordChanged(): void {
    const user = this.currentUser();
    if (user) {
      const updated = { ...user, mustChangePassword: false };
      this.currentUser.set(updated);
      this.storage.setUser(updated);
    }
  }

  private applySession(response: LoginResponse): void {
    this.storage.setAccessToken(response.accessToken, response.expiresIn);
    this.storage.setRefreshToken(response.refreshToken);
    this.storage.setUser(response.user);
    this.currentUser.set(response.user);
  }
}
