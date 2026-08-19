export type Platform = 'wechat' | 'alipay' | 'bank' | 'unknown';

export type Category =
  | '餐饮'
  | '交通'
  | '购物'
  | '居家'
  | '医疗'
  | '娱乐'
  | '教育'
  | '人情'
  | '其他';

export interface OcrBlock {
  text: string;
  confidence: number;
  bbox?: { x: number; y: number; w: number; h: number };
}

export interface OcrResult {
  /** 完整拼接文本 */
  text: string;
  blocks: OcrBlock[];
  /** 产出自哪个引擎 */
  engine: string;
}

export interface ParsedReceipt {
  platform: Platform;
  /** 金额（元），识别失败为 null */
  amount: number | null;
  currency: string;
  /** 归一化时间 'YYYY-MM-DD HH:MM:SS'，识别失败为 null */
  time: string | null;
  merchant: string | null;
  rawText: string;
  /** 金额为 null 时需人工核对 */
  needsReview: boolean;
}

export interface LedgerEntry {
  id: string;
  platform: Platform;
  merchant: string | null;
  amount: number | null;
  category: Category;
  time: string | null;
  currency: string;
  source: 'auto' | 'manual';
  needsReview: boolean;
  rawText: string;
  createdAt: string;
}
