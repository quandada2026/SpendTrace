import type { OcrEngine } from './engine.js';
import type { OcrResult, OcrBlock } from '../types.js';

export interface CloudOcrConfig {
  name: string;
  endpoint: string;
  apiKey: string;
  /** 把云服务商的响应映射成统一结构 */
  map: (json: any) => { text: string; blocks: OcrBlock[] };
}

/**
 * 云端 OCR 模板。填入 endpoint / apiKey / map 即可切换为腾讯、百度、阿里等。
 * 默认以 base64 形式提交图片，Bearer 鉴权，可按需调整。
 */
export class CloudOcrEngine implements OcrEngine {
  readonly name: string;
  constructor(private readonly cfg: CloudOcrConfig) {
    this.name = cfg.name;
  }

  async recognize(input: Buffer | string): Promise<OcrResult> {
    const body = typeof input === 'string' ? input : input.toString('base64');
    const res = await fetch(this.cfg.endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.cfg.apiKey}`,
      },
      body: JSON.stringify({ image: body }),
    });
    const json = await res.json();
    const { text, blocks } = this.cfg.map(json);
    return { text, blocks, engine: this.name };
  }
}
