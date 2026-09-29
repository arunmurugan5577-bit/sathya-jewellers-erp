import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats a gram weight to exactly three decimals.
 *
 * Always three, never trimmed: "5.250 g" and "5.25 g" are the same number but
 * the first one tells the reader the measurement is to the milligram, which is
 * the precision the shop actually weighs to.
 */
@Pipe({ name: 'weight', standalone: true })
export class WeightPipe implements PipeTransform {
  transform(value: number | string | null | undefined, suffix = ' g'): string {
    if (value === null || value === undefined || value === '') {
      return '-';
    }
    const numeric = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(numeric)) {
      return '-';
    }
    return `${numeric.toFixed(3)}${suffix}`;
  }
}
