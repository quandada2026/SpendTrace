import type { LedgerEntry } from '../types.js';

export interface LedgerStore {
  add(entry: LedgerEntry): Promise<void>;
  list(): Promise<LedgerEntry[]>;
  get(id: string): Promise<LedgerEntry | undefined>;
  update(id: string, patch: Partial<LedgerEntry>): Promise<void>;
  remove(id: string): Promise<void>;
}
