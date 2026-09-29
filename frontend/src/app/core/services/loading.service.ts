import { Injectable, computed, signal } from '@angular/core';

/** Counts in-flight HTTP requests for the global progress indicator. */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly inFlight = signal(0);

  readonly isLoading = computed(() => this.inFlight() > 0);

  start(): void {
    this.inFlight.update((count) => count + 1);
  }

  stop(): void {
    this.inFlight.update((count) => Math.max(0, count - 1));
  }
}
