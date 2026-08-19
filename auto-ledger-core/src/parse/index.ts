import type { OcrResult, ParsedReceipt } from '../types.js';
import { extractAmount } from './amount.js';
import { extractTime } from './time.js';
import { extractMerchant } from './merchant.js';
import { detectPlatform } from './platform.js';

/** 把 OCR 结果解析为结构化收据。核心纯函数，可独立单测。 */
export function parseScreenshot(result: OcrResult): ParsedReceipt {
  const text = result.text || '';
  const amount = extractAmount(text);
  const time = extractTime(text);
  const merchant = extractMerchant(text);
  const platform = detectPlatform(text);

  return {
    platform,
    amount,
    time,
    merchant,
    currency: 'CNY',
    rawText: text,
    needsReview: amount === null,
  };
}
