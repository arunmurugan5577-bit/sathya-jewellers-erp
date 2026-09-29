import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

/**
 * Transient feedback.
 *
 * Errors stay on screen roughly twice as long as successes: a success only
 * confirms something the user just did, while an error is information they have
 * to read and act on.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private static readonly SUCCESS_DURATION_MS = 3500;
  private static readonly ERROR_DURATION_MS = 7000;

  private nextId = 1;

  readonly toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.push('success', message, NotificationService.SUCCESS_DURATION_MS);
  }

  error(message: string): void {
    this.push('error', message, NotificationService.ERROR_DURATION_MS);
  }

  warning(message: string): void {
    this.push('warning', message, NotificationService.ERROR_DURATION_MS);
  }

  info(message: string): void {
    this.push('info', message, NotificationService.SUCCESS_DURATION_MS);
  }

  dismiss(id: number): void {
    this.toasts.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  private push(kind: ToastKind, message: string, durationMs: number): void {
    const toast: Toast = { id: this.nextId++, kind, message };
    this.toasts.update((toasts) => [...toasts, toast]);
    setTimeout(() => this.dismiss(toast.id), durationMs);
  }
}
