import { mkdirSync } from 'node:fs';
import { loadConfig } from './config.js';
import { startWatcher, type WatcherDeps } from './watcher.js';
import { createServer } from './server.js';
import { JsonStore } from '../store/json.js';
import { LocalOcrEngine } from '../ocr/local.js';
import { CloudOcrEngine } from '../ocr/cloud.js';
import type { OcrEngine } from '../ocr/engine.js';

function buildEngine(cfg: ReturnType<typeof loadConfig>): OcrEngine {
  if (cfg.ocrMode === 'cloud') {
    return new CloudOcrEngine({
      name: cfg.cloud.name,
      endpoint: cfg.cloud.endpoint,
      apiKey: cfg.cloud.apiKey,
      map: (json: any) => ({ text: json?.text ?? json?.data?.text ?? '', blocks: [] }),
    });
  }
  return new LocalOcrEngine({ lang: cfg.tesseractLang });
}

async function main(): Promise<void> {
  const cfg = loadConfig();
  mkdirSync(cfg.watchDir, { recursive: true });
  mkdirSync(dirnameSafe(cfg.dataFile), { recursive: true });

  const store = new JsonStore(cfg.dataFile);
  const engine = buildEngine(cfg);

  const deps: WatcherDeps = {
    engine,
    store,
    onProcessed: (e, f) =>
      console.log(`[auto] ${e.platform} | ${e.merchant ?? '?'} | ¥${e.amount ?? '?'} <- ${f}`),
    onError: (err, f) => console.error(`[err] ${f}:`, err),
  };

  startWatcher(cfg.watchDir, deps);
  console.log(`[watch] 监听目录: ${cfg.watchDir}`);

  const server = createServer({ store, engine });
  server.listen(cfg.port, () => console.log(`[serve] 打开 http://localhost:${cfg.port}`));
}

function dirnameSafe(p: string): string {
  return p.replace(/[\\/][^\\/]*$/, '');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
