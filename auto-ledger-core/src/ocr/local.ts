import type { OcrEngine } from './engine.js';
import type { OcrResult, OcrBlock } from '../types.js';

export interface LocalOcrOptions {
  /** Tesseract 语言包，默认简体中文+英文 */
  lang?: string;
}

/**
 * 端侧 OCR（Tesseract.js）。免费、隐私、离线、支持中文。
 * 使用动态 import，未安装 tesseract.js 时也不会影响核心引擎加载。
 */
export class LocalOcrEngine implements OcrEngine {
  readonly name = 'local-tesseract';
  constructor(private readonly opts: LocalOcrOptions = {}) {}

  async recognize(input: Buffer | string): Promise<OcrResult> {
    if (typeof input === 'string') {
      return { text: input, blocks: [], engine: this.name };
    }
    const { lang = 'chi_sim+eng' } = this.opts;
    // 动态加载可选依赖
    const Tesseract = (await import('tesseract.js')).default;
    const { data } = await Tesseract.recognize(input, lang);
    const blocks: OcrBlock[] = (data.words ?? []).map((w: any) => ({
      text: w.text,
      confidence: w.confidence,
      bbox: w.bbox
        ? {
            x: w.bbox.x0,
            y: w.bbox.y0,
            w: w.bbox.x1 - w.bbox.x0,
            h: w.bbox.y1 - w.bbox.y0,
          }
        : undefined,
    }));
    return { text: data.text, blocks, engine: this.name };
  }
}
