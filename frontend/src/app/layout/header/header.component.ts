import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { LoadingService } from '../../core/services/loading.service';
import { ThemeService } from '../../core/services/theme.service';

/**
 * Top bar: the mobile menu button, the current user, and the account menu.
 *
 * Also hosts the global progress bar. Putting it here rather than over the
 * content means a slow request never shifts the page while the user is reading
 * it.
 */
@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="header">
      <button
        type="button"
        class="btn btn--ghost btn--icon header__menu"
        aria-label="Toggle navigation"
        (click)="menuToggled.emit()"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M3 6h18M3 12h18M3 18h18" />
        </svg>
      </button>

      <div class="header__spacer"></div>

      <button
        type="button"
        class="theme-switch"
        role="switch"
        [attr.aria-checked]="theme.isDark()"
        [attr.aria-label]="themeLabel()"
        [title]="themeLabel()"
        (click)="theme.toggle()"
      >
        <span class="theme-switch__track">
          <span class="theme-switch__thumb">
            @if (theme.isDark()) {
              <!-- Moon -->
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
              </svg>
            } @else {
              <!-- Sun -->
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="12" r="4" />
                <path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4l1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
              </svg>
            }
          </span>
        </span>
      </button>

      <div class="header__account">
        <button
          type="button"
          class="header__trigger"
          [attr.aria-expanded]="menuOpen()"
          aria-haspopup="menu"
          (click)="menuOpen.set(!menuOpen())"
        >
          <span class="header__avatar" aria-hidden="true">{{ initials() }}</span>
          <span class="header__identity">
            <span class="header__name truncate">{{ auth.user()?.fullName }}</span>
            <span class="header__role truncate">{{ roleLabel() }}</span>
          </span>
        </button>

        @if (menuOpen()) {
          <div class="menu" role="menu">
            <a class="menu__item" routerLink="/change-password" role="menuitem" (click)="menuOpen.set(false)">
              Change password
            </a>
            <button type="button" class="menu__item menu__item--danger" role="menuitem" (click)="signOut()">
              Sign out
            </button>
          </div>
          <!-- Click-away layer: closing on any outside click without a global
               document listener that would fire on every click in the app. -->
          <div class="menu__backdrop" (click)="menuOpen.set(false)"></div>
        }
      </div>

      @if (loading.isLoading()) {
        <div class="header__progress" role="progressbar" aria-label="Loading"></div>
      }
    </header>
  `,
  styles: [
    `
      .header {
        position: sticky;
        top: 0;
        z-index: 500;
        display: flex;
        align-items: center;
        gap: var(--space-3);
        height: var(--header-height);
        padding: 0 var(--space-5);
        background: var(--surface-card);
        border-bottom: 1px solid var(--border-subtle);
      }

      .header__menu {
        display: none;
      }

      .header__menu svg {
        width: 20px;
        height: 20px;
        fill: none;
        stroke: currentColor;
        stroke-width: 1.8;
        stroke-linecap: round;
      }

      .header__spacer {
        flex: 1;
      }

      .header__account {
        position: relative;
      }

      /* --- Theme switch ---------------------------------------------------
       * A sliding track rather than a plain icon button: the position of the
       * thumb shows which mode is active at a glance, where a lone icon is
       * ambiguous (does the moon mean "dark now" or "switch to dark"?).
       */
      .theme-switch {
        padding: 0;
        background: none;
        border: none;
        cursor: pointer;
        line-height: 0;
        border-radius: var(--radius-pill);
      }

      .theme-switch__track {
        display: block;
        position: relative;
        width: 56px;
        height: 30px;
        padding: 3px;
        border-radius: var(--radius-pill);
        background: var(--surface-sunken);
        border: 1px solid var(--border-subtle);
        transition: background var(--transition-base);
      }

      .theme-switch:hover .theme-switch__track {
        border-color: var(--gold);
      }

      .theme-switch__thumb {
        display: grid;
        place-items: center;
        width: 24px;
        height: 24px;
        border-radius: 50%;
        background: var(--brand-deep);
        color: var(--gold-bright);
        transform: translateX(0);
        transition: transform var(--transition-base);
      }

      /* Dark: the thumb slides right and the track deepens. */
      .theme-switch[aria-checked='true'] .theme-switch__track {
        background: var(--brand-deep);
        border-color: var(--brand);
      }

      .theme-switch[aria-checked='true'] .theme-switch__thumb {
        transform: translateX(26px);
        background: var(--gold);
        color: var(--brand-deepest);
      }

      .theme-switch__thumb svg {
        width: 14px;
        height: 14px;
        fill: none;
        stroke: currentColor;
        stroke-width: 2;
        stroke-linecap: round;
      }

      /* Someone who has asked for less motion gets the state change without
       * the slide. */
      @media (prefers-reduced-motion: reduce) {
        .theme-switch__thumb {
          transition: none;
        }
      }

      .header__trigger {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        padding: var(--space-1) var(--space-2);
        background: none;
        border: 1px solid transparent;
        border-radius: var(--radius-md);
        cursor: pointer;
        font: inherit;
        color: inherit;
        max-width: 220px;
      }

      .header__trigger:hover {
        background: var(--surface-hover);
      }

      .header__avatar {
        display: grid;
        place-items: center;
        width: 34px;
        height: 34px;
        border-radius: 50%;
        background: var(--brand-deep);
        color: var(--gold-bright);
        font-size: var(--text-xs);
        font-weight: 700;
        letter-spacing: 0.04em;
        flex-shrink: 0;
      }

      .header__identity {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        min-width: 0;
        line-height: 1.25;
      }

      .header__name {
        font-size: var(--text-base);
        font-weight: 550;
        max-width: 160px;
      }

      .header__role {
        font-size: var(--text-xs);
        color: var(--text-muted);
        max-width: 160px;
      }

      .menu {
        position: absolute;
        top: calc(100% + var(--space-2));
        right: 0;
        z-index: 2;
        min-width: 190px;
        padding: var(--space-1);
        background: var(--surface-raised);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-md);
      }

      .menu__backdrop {
        position: fixed;
        inset: 0;
        z-index: 1;
      }

      .menu__item {
        display: block;
        width: 100%;
        padding: var(--space-2) var(--space-3);
        text-align: left;
        font: inherit;
        font-size: var(--text-base);
        color: var(--text-primary);
        background: none;
        border: none;
        border-radius: var(--radius-sm);
        cursor: pointer;
        text-decoration: none;
      }

      .menu__item:hover {
        background: var(--surface-hover);
        text-decoration: none;
      }

      .menu__item--danger {
        color: var(--danger);
      }

      .header__progress {
        position: absolute;
        left: 0;
        bottom: -1px;
        height: 2px;
        width: 100%;
        background: linear-gradient(90deg, transparent, var(--accent), transparent);
        background-size: 40% 100%;
        background-repeat: no-repeat;
        animation: indeterminate 1.1s ease-in-out infinite;
      }

      @keyframes indeterminate {
        from {
          background-position: -40% 0;
        }
        to {
          background-position: 140% 0;
        }
      }

      @media (max-width: 960px) {
        .header {
          padding: 0 var(--space-4);
        }

        .header__menu {
          display: inline-flex;
        }
      }
    `,
  ],
})
export class HeaderComponent {
  protected readonly auth = inject(AuthService);
  protected readonly loading = inject(LoadingService);
  protected readonly theme = inject(ThemeService);

  readonly menuToggled = output<void>();

  protected readonly menuOpen = signal(false);

  protected initials(): string {
    const name = this.auth.user()?.fullName ?? '';
    return (
      name
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase() ?? '')
        .join('') || '?'
    );
  }

  /** Says what pressing the switch will do, not what it currently shows. */
  protected themeLabel(): string {
    return this.theme.isDark() ? 'Switch to light theme' : 'Switch to dark theme';
  }

  protected roleLabel(): string {
    return this.auth.isAdministrator() ? 'Administrator' : 'User';
  }

  protected signOut(): void {
    this.menuOpen.set(false);
    this.auth.logout();
  }
}
