import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';

import { LoadingService } from '../services/loading.service';

/** Opt out of the global progress bar - used by background polling. */
export const SKIP_LOADING_BAR = new HttpContextToken<boolean>(() => false);

/**
 * Drives the thin progress bar under the header.
 *
 * Counts in-flight requests rather than toggling a boolean, so two overlapping
 * calls do not leave the bar switched off while the second is still running.
 */
export const loadingInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.context.get(SKIP_LOADING_BAR)) {
    return next(request);
  }

  const loading = inject(LoadingService);
  loading.start();

  return next(request).pipe(finalize(() => loading.stop()));
};
