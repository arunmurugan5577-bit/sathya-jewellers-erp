import { Pipe, PipeTransform } from '@angular/core';

const withPaise = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const wholeRupees = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 });

/**
 * Indian digit grouping: 1,14,960.00.
 *
 * `whole` drops the paise column for figures the shop bills in whole rupees, the
 * way the printed receipt does. Display only - the value is never recomputed.
 */
@Pipe({ name: 'inr', standalone: true })
export class InrPipe implements PipeTransform {
  transform(value: number | string | null | undefined, mode: 'paise' | 'whole' = 'paise', symbol = ''): string {
    if (value === null || value === undefined || value === '') {
      return '-';
    }
    const numeric = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(numeric)) {
      return '-';
    }
    const formatter = mode === 'whole' && Number.isInteger(numeric) ? wholeRupees : withPaise;
    return `${symbol}${formatter.format(numeric)}`;
  }
}
