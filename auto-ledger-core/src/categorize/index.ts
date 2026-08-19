import type { Category } from '../types.js';
import { CATEGORY_KEYWORDS } from './dictionary.js';

function match(text: string): Category | null {
  for (const [cat, kws] of Object.entries(CATEGORY_KEYWORDS) as [Category, string[]][]) {
    if (cat === '其他') continue;
    if (kws.some((k) => text.includes(k))) return cat;
  }
  return null;
}

/**
 * 自动归类：
 * 1) 商户名优先匹配；
 * 2) 商户匹配不到时，退到整段 OCR 文本兜底（OCR 把"收款方备注"识别成"备住"等
 *    错字导致商户提取失败时，仍能靠文本里的品牌词归类）；
 * 3) 仍无法匹配归「其他」。
 */
export function categorize(merchant: string | null, fullText: string | null = null): Category {
  if (merchant) {
    const m = match(merchant);
    if (m) return m;
  }
  if (fullText) {
    const t = match(fullText);
    if (t) return t;
  }
  return '其他';
}
