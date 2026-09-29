import { Pipe, PipeTransform } from '@angular/core';

/** Formats a GST rate, dropping a trailing ".00" that adds nothing. */
@Pipe({ name: 'percentage', standalone: true })
export class PercentagePipe implements PipeTransform {
  transform(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '-';
    }
    const numeric = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(numeric)) {
      return '-';
    }
    return `${Number.isInteger(numeric) ? numeric : numeric.toFixed(2)}%`;
  }
}
