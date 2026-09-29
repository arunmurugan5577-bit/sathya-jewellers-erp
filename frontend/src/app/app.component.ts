import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ToastContainerComponent } from './shared/components/toast-container.component';

/**
 * Application root.
 *
 * Deliberately almost empty: the chrome (sidebar, header) belongs to the shell
 * layout, which is itself a route so that the login page can render without it.
 * The toast container lives here because notifications must survive route
 * changes.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastContainerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <app-toast-container />
  `,
})
export class AppComponent {}
