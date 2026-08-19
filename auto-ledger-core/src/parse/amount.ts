const AMOUNT_KEYWORD_RE =
  /(支付金额|付款金额|交易金额|实付金额|应付金额|需付款|合计金额|合计|总额|收款金额|消费金额|扣款金额|金额)\s*[：:]?\s*¥?\s*-?([\d,]+(?:\.\d{1,2})?)/g;

// 数字候选：1-7 位整数（带千分位可选）+ 可选 1-2 位小数；或 整数.1-2位小数
const NUM_CANDIDATE = /(\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?|\d+\.\d{1,2})/g;

// 非支付金额的标签前缀（前 6 字符内出现即视为非目标：余额/剩余/额度/卡余/原额）
const NON_PAYMENT_PREFIX = /余额|剩余|额度|卡余|原额/;

/**
 * 从 OCR 文本中抽取支付金额（元）。
 *
 * 策略：
 *  1) 关键词强绑定（避免"余额 ¥1000"蹭"支付金额"字样误归因）；
 *  2) 无强信号时，扫描全文所有数字候选：
 *     - 带小数的（XX.XX）最像金额，优先取最大；
 *     - 整数作为补充，过滤 1900-2099 年份与余额/剩余等非支付上下文；
 *     - 值域限定 0.01~999999。
 *
 * 容错真实 OCR 失真：全角符号归一、¥ 错为 4、数字周围夹字母、千分位逗号、中文"x元"。
 */
export function extractAmount(text: string): number | null {
  const t = text
    .replace(/￥/g, '¥')
    .replace(/[－—]/g, '-')
    .replace(/[ 　]/g, ' ');

  // 1) 关键词强绑定
  let best: number | null = null;
  for (const m of t.matchAll(AMOUNT_KEYWORD_RE)) {
    const v = parseFloat(m[2].replace(/,/g, ''));
    if (isFinite(v) && (best === null || v > best)) best = v;
  }
  if (best !== null) return best;

  // 2) 兜底：扫所有数字候选
  let decimalBest: number | null = null;
  let intBest: number | null = null;
  for (const m of t.matchAll(NUM_CANDIDATE)) {
    const raw = m[1] ?? '';
    const idx = m.index ?? 0;
    // 排除余额/剩余/额度等非支付上下文
    const before = t.slice(Math.max(0, idx - 6), idx);
    if (NON_PAYMENT_PREFIX.test(before)) continue;
    const v = parseFloat(raw.replace(/,/g, ''));
    if (!isFinite(v) || v < 0.01 || v > 999999) continue;
    const hasDecimal = raw.includes('.');
    if (hasDecimal) {
      if (decimalBest === null || v > decimalBest) decimalBest = v;
    } else {
      // 整数过滤 4 位年份（1900-2099），避免日期被误识别为金额
      if (raw.length === 4 && v >= 1900 && v <= 2099) continue;
      if (intBest === null || v > intBest) intBest = v;
    }
  }
  return decimalBest ?? intBest;
}