import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';

/**
 * Renders the first validation message for a control.
 *
 * Handles both sources of truth: Angular's own validators and the
 * `fieldErrors` a server response attaches via `setErrors({ server: '...' })`.
 * A server message always wins, because it knows things the client cannot -
 * that a serial number was taken half a second ago, for instance.
 */
@Component({
  selector: 'app-field-error',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="field__error">{{ message() }}</p>
  `,
})
export class FieldErrorComponent {
  readonly control = input.required<AbstractControl | null>();
  readonly label = input('This field');

  protected readonly message = computed(() => {
    const control = this.control();
    if (!control || !control.errors || !(control.dirty || control.touched)) {
      return '';
    }

    const errors = control.errors;
    if (typeof errors['server'] === 'string') {
      return errors['server'];
    }
    if (errors['required']) {
      return `${this.label()} is required.`;
    }
    if (errors['email']) {
      return 'Enter a valid e-mail address.';
    }
    if (errors['minlength']) {
      const requirement = errors['minlength'] as { requiredLength: number };
      return `${this.label()} must be at least ${requirement.requiredLength} characters.`;
    }
    if (errors['maxlength']) {
      const requirement = errors['maxlength'] as { requiredLength: number };
      return `${this.label()} must not exceed ${requirement.requiredLength} characters.`;
    }
    if (errors['min']) {
      const requirement = errors['min'] as { min: number };
      return `${this.label()} must be at least ${requirement.min}.`;
    }
    if (errors['max']) {
      const requirement = errors['max'] as { max: number };
      return `${this.label()} must not exceed ${requirement.max}.`;
    }
    if (typeof errors['pattern'] === 'object') {
      return `${this.label()} has an invalid format.`;
    }
    if (typeof errors['message'] === 'string') {
      return errors['message'];
    }
    return `${this.label()} is invalid.`;
  });
}
