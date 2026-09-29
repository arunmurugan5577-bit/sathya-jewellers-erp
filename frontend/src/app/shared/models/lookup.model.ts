/** A dropdown option. Lookup endpoints only ever return active records. */
export interface Lookup {
  id: number;
  name: string;
  code?: string | null;
}
