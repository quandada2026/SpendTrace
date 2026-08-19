import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { processImageFile } from '../src/shell/watcher.js';
import { MemoryStore } from '../src/store/memory.js';
import type { OcrEngine, OcrResult } from '../src/types.js';

// 测试用引擎：把图片二进制当作 UTF-8 文本（模拟"OCR 已完成"），避免依赖 Tesseract。
const FileTextEngine: OcrEngine = {
  name: 'file-text',
  async recognize(input: Buffer | string): Promise<OcrResult> {
    const text = typeof input === 'string' ? input : input.toString('utf8');
    return { text, blocks: [], engine: 'file-text' };
  },
};

const SAMPLE = `微信支付
支付成功
收款方备注：星巴克咖啡
金额 ¥32.00
2026-08-18 09:30:12`;

test('processImageFile: 读图 → 核心引擎 → 入库', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'alw-'));
  const file = join(dir, 'shot.txt'); // 测试用 .txt；watcher 实际按图片扩展名过滤
  await writeFile(file, SAMPLE, 'utf8');

  const store = new MemoryStore();
  const entry = await processImageFile(file, { engine: FileTextEngine, store });

  assert.equal(entry.amount, 32.0);
  assert.equal(entry.merchant, '星巴克咖啡');
  assert.equal((await store.list()).length, 1);

  await rm(dir, { recursive: true, force: true });
});
