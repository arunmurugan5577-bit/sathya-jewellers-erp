import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

/**
 * What the user has chosen. `system` means "whatever the operating system
 * says", which is the state the application starts in.
 */
export type ThemePreference = 'system' | 'light' | 'dark';

/**
 * Light / dark theme selection.
 *
 * Three states rather than two. Defaulting to `system` means a shop whose
 * machines are set to dark gets dark without anyone configuring anything, while
 * the switch still lets someone override it for their own screen - shop
 * lighting rarely matches what Windows assumes.
 *
 * The chosen theme is applied as `data-theme` on the root element, which the
 * token stylesheet keys off. A small inline script in index.html sets the same
 * attribute before the first paint, so a user who prefers dark never sees a
 * white flash while Angular boots.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  /** Kept in step with the inline bootstrap script in index.html. */
  static readonly STORAGE_KEY = 'jewellery-erp.theme';

  private readonly document = inject(DOCUMENT);

  private readonly preference = signal<ThemePreference>('system');
  private readonly systemPrefersDark = signal(false);

  /** What is actually on screen right now, preference resolved. */
  readonly isDark = computed(() =>
    this.preference() === 'system' ? this.systemPrefersDark() : this.preference() === 'dark',
  );

  readonly current = this.preference.asReadonly();

  /** True while following the operating system rather than an explicit choice. */
  readonly followsSystem = computed(() => this.preference() === 'system');

  constructor() {
    const media = this.document.defaultView?.matchMedia('(prefers-color-scheme: dark)');
    if (media) {
      this.systemPrefersDark.set(media.matches);
      // Track later OS changes, so a machine that switches at sunset follows
      // along while the user is still signed in.
      media.addEventListener('change', (event) => this.systemPrefersDark.set(event.matches));
    }

    this.preference.set(this.read());
    this.apply();
  }

  /**
   * Flips between light and dark.
   *
   * From `system` it moves to the opposite of what is currently showing, which
   * is what a user pressing the button expects - not a no-op that lands them on
   * the theme they already had.
   */
  toggle(): void {
    this.set(this.isDark() ? 'light' : 'dark');
  }

  set(preference: ThemePreference): void {
    this.preference.set(preference);
    this.write(preference);
    this.apply();
  }

  /** Hands control back to the operating system. */
  useSystem(): void {
    this.set('system');
  }

  private apply(): void {
    const root = this.document.documentElement;
    if (this.preference() === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', this.preference());
    }
  }

  /* Storage can throw in a private window or when site data is blocked, so both
   * accessors degrade to "follow the system" rather than breaking the page. */
  private read(): ThemePreference {
    try {
      const stored = localStorage.getItem(ThemeService.STORAGE_KEY);
      return stored === 'light' || stored === 'dark' ? stored : 'system';
    } catch {
      return 'system';
    }
  }

  private write(preference: ThemePreference): void {
    try {
      if (preference === 'system') {
        localStorage.removeItem(ThemeService.STORAGE_KEY);
      } else {
        localStorage.setItem(ThemeService.STORAGE_KEY, preference);
      }
    } catch {
      /* The choice simply will not survive a reload. */
    }
  }
}
