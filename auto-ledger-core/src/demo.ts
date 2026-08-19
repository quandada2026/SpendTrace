import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { processScreenshot, MockEngine, MemoryStore } from './index.js';

const files = ['wechat.txt', 'alipay.txt', 'bank.txt'];
const store = new MemoryStore();

for (const f of files) {
  const path = fileURLToPath(new URL(`../samples/${f}`, import.meta.url));
  const text = await readFile(path, 'utf8');
  const entry = await processScreenshot(new MockEngine(text), text, store);
  console.log(`\n=== ${f} ===`);
  console.log(JSON.stringify(entry, null, 2));
}

const all = await store.list();
const review = all.filter((e) => e.needsReview).length;
console.log(`\n共记账 ${all.length} 笔，待人工核对 ${review} 笔`);
