import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ApiService } from '../core/services/api.service';
import { ConfirmDialogComponent } from '../shared/components/confirm-dialog.component';
import { HeaderComponent } from './header/header.component';
import { SidebarComponent } from './sidebar/sidebar.component';

/**
 * The application chrome: sidebar, header, content area.
 *
 * A route in its own right, with every authenticated screen as a child. That is
 * what lets the login page render full-bleed without the shell, and what keeps
 * the sidebar from being rebuilt on every navigation.
 *
 * Below 960px the sidebar becomes an off-canvas drawer, so the content keeps the
 * full width on a phone at the counter.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, HeaderComponent, ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shell">
      <app-sidebar
        [open]="drawerOpen()"
        [shopName]="shopName()"
        (navigated)="drawerOpen.set(false)"
      />

      @if (drawerOpen()) {
        <div class="shell__scrim" (click)="drawerOpen.set(false)"></div>
      }

      <div class="shell__main">
        <app-header (menuToggled)="drawerOpen.set(!drawerOpen())" />
        <main class="shell__content">
          <router-outlet />
        </main>
      </div>
    </div>

    <app-confirm-dialog />
  `,
  styles: [
    `
      .shell {
        display: flex;
        min-height: 100vh;
      }

      .shell__main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }

      .shell__content {
        flex: 1;
        width: 100%;
        max-width: var(--content-max-width);
        margin: 0 auto;
        padding: var(--space-5);
      }

      .shell__scrim {
        position: fixed;
        inset: 0;
        z-index: 650;
        background: rgba(20, 18, 15, 0.4);
      }

      @media (max-width: 960px) {
        .shell__content {
          padding: var(--space-4);
        }
      }

      @media (max-width: 640px) {
        .shell__content {
          padding: var(--space-3);
        }
      }
    `,
  ],
})
export class ShellComponent {
  private readonly api = inject(ApiService);

  protected readonly drawerOpen = signal(false);
  protected readonly shopName = signal('Jewellery ERP');

  constructor() {
    // The shop name brands the sidebar, so every signed-in user gets it - the
    // dedicated /branding endpoint returns the name only and needs no
    // permission. Reading the full profile still requires SHOP_SETTINGS_VIEW.
    this.api.get<{ shopName: string }>('/shop-settings/branding').subscribe({
      next: (branding) => this.shopName.set(branding.shopName),
      error: () => {
        /* Branding is cosmetic - the generic name is a fine fallback. */
      },
    });
  }
}
