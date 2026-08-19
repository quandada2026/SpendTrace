import { readFile } from 'node:fs/promises';
import { basename, extname } from 'node:path';
import chokidar, { type FSWatcher } from 'chokidar';
import type { OcrEngine } from '../ocr/engine.js';
import type { LedgerStore } from '../store/types.js';
import { processScreenshot } from '../index.js';
import type { LedgerEntry } from '../types.js';

export interface WatcherDeps {
  engine: OcrEngine;
  store: LedgerStore;
  /** 监听的图片扩展名（小写，含点） */
  extensions?: string[];
  onProcessed?: (entry: LedgerEntry, file: string) => void;
  onError?: (err: unknown, file: string) => void;
}

const DEFAULT_EXT = ['.png', '.jpg', '.jpeg', '.webp', '.bmp'];

/**
 * 处理单个截图文件：读图 → 核心引擎 → 入库。
 * 抽成独立函数便于直接单测（绕过 chokidar）。
 */
export async function processImageFile(
  filePath: string,
  deps: WatcherDeps,
): Promise<LedgerEntry> {
  const buf = await readFile(filePath);
  const entry = await processScreenshot(deps.engine, buf, deps.store, {
    source: 'auto',
  });
  deps.onProcessed?.(entry, filePath);
  return entry;
}

/**
 * 启动文件夹看守：监听目录，新截图落盘即自动记账（零操作闭环）。
 * 原理解释：手机/电脑截图 → 系统写入截图目录 → 观察者捕获"新增文件"事件 → 触发 OCR 记账。
 * 这与安卓 MediaStore 内容观察者的触发逻辑完全一致，仅文件来源不同。
 */
export function startWatcher(dir: string, deps: WatcherDeps): FSWatcher {
  const exts = deps.extensions ?? DEFAULT_EXT;
  const watcher = chokidar.watch(dir, {
    ignoreInitial: true,
    awaitWriteFinish: { stabilityThreshold: 600, pollInterval: 200 },
  });

  const handler = (file: string) => {
    if (!exts.includes(extname(file).toLowerCase())) return;
    processImageFile(file, deps).catch((err) => deps.onError?.(err, file));
  };

  watcher.on('add', handler);
  watcher.on('error', (err) => deps.onError?.(err, '<watcher>'));
  return watcher;
}
