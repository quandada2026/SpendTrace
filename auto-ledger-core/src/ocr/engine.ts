import type { OcrResult } from '../types.js';

/**
 * OCR 引擎抽象。所有平台（本地/云端/测试）统一实现该接口。
 * input 为 Buffer 时表示图像二进制；为 string 时表示已是识别文本（测试/演示用）。
 */
export interface OcrEngine {
  readonly name: string;
  recognize(input: Buffer | string): Promise<OcrResult>;
}
