import { randomUUID } from 'node:crypto';
import type { OcrEngine } from './ocr/engine.js';
import type { LedgerStore } from './store/types.js';
import type { LedgerEntry } from './types.js';
import { parseScreenshot } from './parse/index.js';
import { categorize } from './categorize/index.js';

export interface ProcessOptions {
  source?: 'auto' | 'manual';
}

/**
 * 编排：OCR → 结构化解析 → 自动分类 → 入库。
 * 这是平台外壳（Android/桌面）唯一需要调用的入口。
 */
export async function processScreenshot(
  engine: OcrEngine,
  input: Buffer | string,
  store: LedgerStore,
  opts: ProcessOptions = {},
): Promise<LedgerEntry> {
  const ocr = await engine.recognize(input);
  const parsed = parseScreenshot(ocr);
  const category = categorize(parsed.merchant, parsed.rawText);

  const entry: LedgerEntry = {
    id: randomUUID(),
    platform: parsed.platform,
    merchant: parsed.merchant,
    amount: parsed.amount,
    category,
    time: parsed.time,
    currency: parsed.currency,
    source: opts.source ?? 'auto',
    needsReview: parsed.needsReview,
    rawText: parsed.rawText,
    createdAt: new Date().toISOString(),
  };

  await store.add(entry);
  return entry;
}

export * from './types.js';
export * from './ocr/engine.js';
export * from './store/types.js';
export { parseScreenshot } from './parse/index.js';
export { categorize } from './categorize/index.js';
export { MockEngine } from './ocr/mock.js';
export { LocalOcrEngine } from './ocr/local.js';
export { CloudOcrEngine } from './ocr/cloud.js';
export { MemoryStore } from './store/memory.js';
export { JsonStore } from './store/json.js';
