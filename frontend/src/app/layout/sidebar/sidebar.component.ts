import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { NAV_SECTIONS, NavSection } from '../nav.config';

/**
 * Primary navigation.
 *
 * Sections and entries are filtered by permission, and a section with nothing
 * visible disappears with its heading. The point is not secrecy - the server
 * decides what a user may do - it is that a menu full of dead ends is worse than
 * a short one.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="sidebar" [class.sidebar--open]="open()" aria-label="Main navigation">
      <div class="sidebar__brand">
        <img class="sidebar__logo" src="assets/brand/logo-wide.png" [alt]="shopName()" width="774" height="258" />
      </div>

      <div class="sidebar__scroll">
        @for (section of visibleSections(); track section.label ?? $index) {
          <div class="sidebar__section">
            @if (section.label) {
              <p class="sidebar__heading">{{ section.label }}</p>
            }
            @for (item of section.items; track item.route) {
              <a
                class="sidebar__link"
                [routerLink]="item.route"
                routerLinkActive="sidebar__link--active"
                [routerLinkActiveOptions]="{ exact: exactRoutes.includes(item.route) }"
                (click)="navigated.emit()"
              >
                <svg class="sidebar__icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path [attr.d]="item.icon" />
                </svg>
                <span>{{ item.label }}</span>
              </a>
            }
          </div>
        }
      </div>
    </nav>
  `,
  styles: [
    `
      .sidebar {
        display: flex;
        flex-direction: column;
        width: var(--sidebar-width);
        height: 100vh;
        background: var(--surface-sidebar);
        color: var(--text-sidebar);
        position: sticky;
        top: 0;
      }

      .sidebar__brand {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        justify-content: center;
        min-height: var(--header-height);
        padding: var(--space-3) var(--space-4);
        border-bottom: 1px solid rgba(255, 255, 255, 0.07);
        flex-shrink: 0;
      }

      /* The shop's own logo, on its maroon ground, as wide as the sidebar allows. */
      .sidebar__logo {
        display: block;
        width: 100%;
        max-width: 220px;
        height: auto;
        border-radius: var(--radius-sm);
      }

      .sidebar__scroll {
        flex: 1;
        overflow-y: auto;
        padding: var(--space-4) var(--space-3) var(--space-6);
      }

      .sidebar__section + .sidebar__section {
        margin-top: var(--space-5);
      }

      .sidebar__heading {
        padding: 0 var(--space-3);
        margin-bottom: var(--space-2);
        font-size: var(--text-xs);
        font-weight: 600;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: #7d766a;
      }

      .sidebar__link {
        position: relative;
        display: flex;
        align-items: center;
        gap: var(--space-3);
        padding: var(--space-2) var(--space-3);
        border-radius: var(--radius-md);
        color: inherit;
        font-size: var(--text-base);
        font-weight: 500;
        text-decoration: none;
        transition:
          background var(--transition-fast),
          color var(--transition-fast);
      }

      .sidebar__link:hover {
        background: var(--surface-sidebar-hover);
        color: #fbf5ef;
        text-decoration: none;
      }

      .sidebar__link--active {
        background: var(--surface-sidebar-active);
        color: var(--text-sidebar-active);
        font-weight: 600;
      }

      /* A gold bar marks the current page. Colour alone would be a weak signal
       * against maroon, and invisible to anyone who cannot distinguish it. */
      .sidebar__link--active::before {
        content: '';
        position: absolute;
        left: calc(-1 * var(--space-3));
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 22px;
        border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
        background: linear-gradient(180deg, var(--gold-bright), var(--gold));
      }

      .sidebar__icon {
        width: 18px;
        height: 18px;
        flex-shrink: 0;
        fill: none;
        stroke: currentColor;
        stroke-width: 1.6;
        stroke-linecap: round;
        stroke-linejoin: round;
      }

      /* Off-canvas drawer below the tablet breakpoint. */
      @media (max-width: 960px) {
        .sidebar {
          position: fixed;
          inset: 0 auto 0 0;
          z-index: 700;
          transform: translateX(-100%);
          transition: transform var(--transition-base);
          box-shadow: var(--shadow-lg);
        }

        .sidebar--open {
          transform: translateX(0);
        }
      }
    `,
  ],
})
export class SidebarComponent {
  /** List routes whose child pages (e.g. /sales/new) have their own sidebar entry. */
  protected readonly exactRoutes = ['/inventory', '/sales', '/old-metal'];

  private readonly auth = inject(AuthService);

  readonly open = input(false);
  /** Used as the logo's alternative text. */
  readonly shopName = input('Jewellery ERP');

  /** Emitted when a link is followed, so the mobile drawer can close itself. */
  readonly navigated = output<void>();


  protected readonly visibleSections = computed<NavSection[]>(() => {
    // Reading the signal keeps this in step with a permission change picked up
    // at token refresh.
    this.auth.permissions();

    return NAV_SECTIONS.map((section) => ({
      ...section,
      items: section.items.filter((item) => !item.permissions || this.auth.hasAny(item.permissions)),
    })).filter((section) => section.items.length > 0);
  });
}
