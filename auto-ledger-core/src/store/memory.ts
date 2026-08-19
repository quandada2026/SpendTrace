import type { LedgerEntry } from '../types.js';
import type { LedgerStore } from './types.js';

/** 内存存储，用于测试与演示。 */
export class MemoryStore implements LedgerStore {
  private items: LedgerEntry[] = [];
  async add(e: LedgerEntry): Promise<void> {
    this.items.push(e);
  }
  async list(): Promise<LedgerEntry[]> {
    return [...this.items];
  }
  async get(id: string): Promise<LedgerEntry | undefined> {
    return this.items.find((i) => i.id === id);
  }
  async update(id: string, patch: Partial<LedgerEntry>): Promise<void> {
    const i = this.items.findIndex((x) => x.id === id);
    if (i >= 0) this.items[i] = { ...this.items[i], ...patch };
  }
  async remove(id: string): Promise<void> {
    this.items = this.items.filter((x) => x.id !== id);
  }
}
