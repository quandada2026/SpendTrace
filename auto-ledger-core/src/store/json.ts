import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';
import type { LedgerEntry } from '../types.js';
import type { LedgerStore } from './types.js';

/** JSON 文件存储，用于桌面/CLI 场景的本地持久化。 */
export class JsonStore implements LedgerStore {
  constructor(private readonly file: string) {}

  private async readAll(): Promise<LedgerEntry[]> {
    try {
      const t = await readFile(this.file, 'utf8');
      return JSON.parse(t) as LedgerEntry[];
    } catch {
      return [];
    }
  }

  private async writeAll(items: LedgerEntry[]): Promise<void> {
    await mkdir(dirname(this.file), { recursive: true });
    await writeFile(this.file, JSON.stringify(items, null, 2), 'utf8');
  }

  async add(e: LedgerEntry): Promise<void> {
    const all = await this.readAll();
    all.push(e);
    await this.writeAll(all);
  }
  async list(): Promise<LedgerEntry[]> {
    return this.readAll();
  }
  async get(id: string): Promise<LedgerEntry | undefined> {
    return (await this.readAll()).find((i) => i.id === id);
  }
  async update(id: string, patch: Partial<LedgerEntry>): Promise<void> {
    const all = await this.readAll();
    const i = all.findIndex((x) => x.id === id);
    if (i >= 0) {
      all[i] = { ...all[i], ...patch };
      await this.writeAll(all);
    }
  }
  async remove(id: string): Promise<void> {
    const all = await this.readAll();
    const next = all.filter((x) => x.id !== id);
    if (next.length !== all.length) await this.writeAll(next);
  }
}
