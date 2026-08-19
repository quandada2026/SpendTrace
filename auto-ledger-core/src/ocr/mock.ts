import type { OcrEngine } from './engine.js';
import type { OcrResult } from '../types.js';

/** 测试/演示用：直接返回预置文本，跳过真实 OCR。 */
export class MockEngine implements OcrEngine {
  readonly name = 'mock';
  constructor(private readonly text: string) {}
  async recognize(_input: Buffer | string): Promise<OcrResult> {
    return { text: this.text, blocks: [{ text: this.text, confidence: 1 }], engine: 'mock' };
  }
}
