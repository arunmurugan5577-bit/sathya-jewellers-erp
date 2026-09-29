import { WeightPipe } from './weight.pipe';

describe('WeightPipe', () => {
  const pipe = new WeightPipe();

  it('always shows three decimals, so the precision is visible', () => {
    expect(pipe.transform(5.25)).toBe('5.250 g');
    expect(pipe.transform(12)).toBe('12.000 g');
  });

  it('accepts the string form a JSON number may arrive as', () => {
    expect(pipe.transform('5.250')).toBe('5.250 g');
  });

  it('renders a dash rather than NaN for a missing value', () => {
    expect(pipe.transform(null)).toBe('-');
    expect(pipe.transform(undefined)).toBe('-');
    expect(pipe.transform('')).toBe('-');
    expect(pipe.transform('not a number')).toBe('-');
  });

  it('can omit the unit for use inside a labelled metric', () => {
    expect(pipe.transform(5.25, '')).toBe('5.250');
  });
});
