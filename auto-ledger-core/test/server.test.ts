import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AddressInfo } from 'node:net';
import { MemoryStore } from '../src/store/memory.js';
import { createServer } from '../src/shell/server.js';
import type { OcrEngine, OcrResult } from '../src/types.js';

const FileTextEngine: OcrEngine = {
  name: 'file-text',
  async recognize(input: Buffer | string): Promise<OcrResult> {
    const text = typeof input === 'string' ? input : input.toString('utf8');
    return { text, blocks: [], engine: 'file-text' };
  },
};

const SAMPLE = `支付宝
支付成功
商家：麦当劳
-¥25.50
2026-08-18 12:01:05`;

async function startServer() {
  const store = new MemoryStore();
  const server = createServer({ store, engine: FileTextEngine });
  await new Promise<void>((r) => server.listen(0, () => r()));
  const port = (server.address() as AddressInfo).port;
  return { server, store, base: `http://127.0.0.1:${port}` };
}

const sampleDataUrl = () =>
  'data:image/png;base64,' + Buffer.from(SAMPLE, 'utf8').toString('base64');

test('GET /api/entries 初始为空', async () => {
  const { server, base } = await startServer();
  try {
    const res = await fetch(`${base}/api/entries`);
    const data = (await res.json()) as unknown[];
    assert.equal(Array.isArray(data), true);
    assert.equal(data.length, 0);
  } finally {
    server.close();
  }
});

test('POST /api/ocr 手动上传 → 记账', async () => {
  const { server, base } = await startServer();
  try {
    const res = await fetch(`${base}/api/ocr`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image: sampleDataUrl() }),
    });
    const entry = await res.json();
    assert.equal(entry.amount, 25.5);
    assert.equal(entry.source, 'manual');

    const list = (await (await fetch(`${base}/api/entries`)).json()) as unknown[];
    assert.equal(list.length, 1);
  } finally {
    server.close();
  }
});

test('PATCH 修正 + DELETE 删除', async () => {
  const { server, base, store } = await startServer();
  try {
    const entry = await (
      await fetch(`${base}/api/ocr`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ image: sampleDataUrl() }),
      })
    ).json();

    await fetch(`${base}/api/entries/${entry.id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount: 30, needsReview: false }),
    });
    assert.equal((await store.list())[0].amount, 30);
    assert.equal((await store.list())[0].needsReview, false);

    await fetch(`${base}/api/entries/${entry.id}`, { method: 'DELETE' });
    assert.equal((await store.list()).length, 0);
  } finally {
    server.close();
  }
});
