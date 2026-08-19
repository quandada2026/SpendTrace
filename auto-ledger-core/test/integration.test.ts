import { test } from 'node:test';
import assert from 'node:assert/strict';
import { processScreenshot } from '../src/index.js';
import { MockEngine } from '../src/ocr/mock.js';
import { MemoryStore } from '../src/store/memory.js';

const wechat = `微信支付
支付成功
收款方备注：星巴克咖啡
金额 ¥32.00
2026-08-18 09:30:12`;

const noAmount = `微信支付
支付成功
收款方备注：某小店
2026-08-18 09:30:12`;

test('端到端：自动记账并入库', async () => {
  const store = new MemoryStore();
  const entry = await processScreenshot(new MockEngine(wechat), wechat, store);
  assert.equal(entry.amount, 32.0);
  assert.equal(entry.category, '餐饮');
  assert.equal(entry.source, 'auto');
  assert.equal(entry.needsReview, false);

  const all = await store.list();
  assert.equal(all.length, 1);
  assert.equal(all[0].id, entry.id);
});

test('金额缺失：标记待核对但仍入库', async () => {
  const store = new MemoryStore();
  const entry = await processScreenshot(new MockEngine(noAmount), noAmount, store);
  assert.equal(entry.amount, null);
  assert.equal(entry.needsReview, true);

  const all = await store.list();
  assert.equal(all.length, 1);
});
