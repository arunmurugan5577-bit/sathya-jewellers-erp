import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';

import { ApiError, isApiError } from '../models/api-error.model';

/**
 * Projects a server validation failure back onto the form that caused it.
 *
 * The backend validates independently of the client, so it regularly rejects
 * something the client thought was fine - a serial number taken a moment ago, a
 * purity that belongs to another item type. Those messages belong next to the
 * control, not in a banner the user has to translate back into a field.
 *
 * @returns the messages that could not be matched to a control, so the caller
 *     can show them at form level rather than dropping them silently
 */
export function applyServerErrors(form: FormGroup, error: unknown): string[] {
  const apiError = extractApiError(error);
  if (!apiError) {
    return [];
  }

  const unmatched: string[] = [];
  const fieldErrors = apiError.fieldErrors ?? {};

  for (const [field, message] of Object.entries(fieldErrors)) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: message });
      control.markAsTouched();
      control.markAsDirty();
    } else {
      unmatched.push(message);
    }
  }

  // A 409 or a business rule failure often has no field map at all - surface the
  // top level message rather than leaving the user with a silent failure.
  if (Object.keys(fieldErrors).length === 0 && apiError.message) {
    unmatched.push(apiError.message);
  }

  return unmatched;
}

/**
 * Clears server errors before a resubmit.
 *
 * Without this, a field the user has just corrected would keep showing the
 * previous rejection until the server replies again.
 */
export function clearServerErrors(form: FormGroup): void {
  for (const control of Object.values(form.controls)) {
    if (!control.errors?.['server']) {
      continue;
    }
    const { server: _removed, ...rest } = control.errors;
    control.setErrors(Object.keys(rest).length > 0 ? rest : null);
  }
}

/** Marks everything touched so untouched invalid fields show their message. */
export function markAllTouched(form: FormGroup): void {
  form.markAllAsTouched();
  form.updateValueAndValidity({ emitEvent: false });
}

function extractApiError(error: unknown): ApiError | null {
  if (error instanceof HttpErrorResponse && isApiError(error.error)) {
    return error.error;
  }
  return isApiError(error) ? error : null;
}
