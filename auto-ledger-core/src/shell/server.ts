import { createServer as httpCreateServer, type Server } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import type { OcrEngine } from '../ocr/engine.js';
import type { LedgerStore } from '../store/types.js';
import { processScreenshot } from '../index.js';
import type { LedgerEntry } from '../types.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const UI_DIR = join(__dirname, '..', 'ui');

const MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
};

export interface ServerDeps {
  store: LedgerStore;
  engine: OcrEngine;
}

function sendJson(res: import('node:http').ServerResponse, status: number, data: unknown): void {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(data));
}

function readBody(req: import('node:http').IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

/** 创建本地查看/补录服务（不监听端口，便于测试）。 */
export function createServer(deps: ServerDeps): Server {
  return httpCreateServer(async (req, res) => {
    const url = new URL(req.url ?? '/', 'http://localhost');
    const path = url.pathname;

    try {
      // 静态 UI
      if (path === '/' || path === '/index.html') {
        const html = await readFile(join(UI_DIR, 'index.html'));
        res.writeHead(200, { 'Content-Type': MIME['.html'] });
        return res.end(html);
      }
      if (path === '/app.js') {
        const js = await readFile(join(UI_DIR, 'app.js'));
        res.writeHead(200, { 'Content-Type': MIME['.js'] });
        return res.end(js);
      }

      // 账本列表（按创建时间倒序）
      if (path === '/api/entries' && req.method === 'GET') {
        const all = await deps.store.list();
        all.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
        return sendJson(res, 200, all);
      }

      // 手动上传截图 → OCR → 记账（给"无双击截图手机"的兜底路径）
      if (path === '/api/ocr' && req.method === 'POST') {
        const body = JSON.parse(await readBody(req));
        const dataUrl: string = body?.image;
        if (!dataUrl || !dataUrl.startsWith('data:')) {
          return sendJson(res, 400, { error: 'missing image' });
        }
        const base64 = dataUrl.split(',')[1] ?? '';
        const buf = Buffer.from(base64, 'base64');
        const entry = await processScreenshot(deps.engine, buf, deps.store, {
          source: 'manual',
        });
        return sendJson(res, 200, entry);
      }

      // 修正 / 删除
      const m = path.match(/^\/api\/entries\/([\w-]+)$/);
      if (m && (req.method === 'PATCH' || req.method === 'DELETE')) {
        const id = m[1];
        if (req.method === 'DELETE') {
          await deps.store.remove(id);
          return sendJson(res, 200, { ok: true });
        }
        const patch = JSON.parse(await readBody(req));
        await deps.store.update(id, patch);
        return sendJson(res, 200, { ok: true });
      }

      sendJson(res, 404, { error: 'not found' });
    } catch (err) {
      sendJson(res, 500, { error: String(err) });
    }
  });
}
