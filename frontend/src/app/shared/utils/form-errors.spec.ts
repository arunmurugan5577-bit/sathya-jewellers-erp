import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';

import { applyServerErrors, clearServerErrors } from './form-errors';

describe('form-errors', () => {
  function buildForm(): FormGroup {
    return new FormGroup({
      serialNumber: new FormControl(''),
      weightGrams: new FormControl(null),
    });
  }

  function conflict(fieldErrors?: Record<string, string>, message = 'Validation failed') {
    return new HttpErrorResponse({
      status: 409,
      error: {
        timestamp: '2026-09-15T00:00:00Z',
        status: 409,
        error: 'Conflict',
        message,
        fieldErrors,
      },
    });
  }

  it('puts a server field error on the matching control', () => {
    const form = buildForm();

    const unmatched = applyServerErrors(form, conflict({ serialNumber: 'Serial number 123456 already exists.' }));

    expect(unmatched).toEqual([]);
    expect(form.controls['serialNumber'].errors?.['server']).toBe(
      'Serial number 123456 already exists.',
    );
    expect(form.controls['serialNumber'].touched).toBeTrue();
  });

  it('returns messages for fields the form does not have, instead of dropping them', () => {
    const form = buildForm();

    const unmatched = applyServerErrors(form, conflict({ purityId: 'Purity belongs to Silver.' }));

    expect(unmatched).toEqual(['Purity belongs to Silver.']);
  });

  it('surfaces the top level message when there are no field errors', () => {
    const form = buildForm();

    const unmatched = applyServerErrors(form, conflict(undefined, 'This item type is still referenced.'));

    expect(unmatched).toEqual(['This item type is still referenced.']);
  });

  it('ignores an error that is not an API error envelope', () => {
    const form = buildForm();

    expect(applyServerErrors(form, new Error('network down'))).toEqual([]);
    expect(form.controls['serialNumber'].errors).toBeNull();
  });

  it('clears only the server error, keeping client validation intact', () => {
    const form = buildForm();
    form.controls['serialNumber'].setErrors({ required: true, server: 'Already exists.' });

    clearServerErrors(form);

    expect(form.controls['serialNumber'].errors).toEqual({ required: true });
  });

  it('clears the error object entirely when the server error was the only one', () => {
    const form = buildForm();
    form.controls['serialNumber'].setErrors({ server: 'Already exists.' });

    clearServerErrors(form);

    expect(form.controls['serialNumber'].errors).toBeNull();
  });
});
