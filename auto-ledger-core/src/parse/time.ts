function ymd(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/**
 * 抽取时间并归一化为 'YYYY-MM-DD HH:MM:SS'。
 * 支持：
 *  - 2026-08-18 09:30:12 / 2026/08/18 09:30 / 2026.08.18
 *  - 今天 09:05 / 昨日 22:10（按本地日期推导）
 * 无法识别返回 null。
 */
export function extractTime(text: string): string | null {
  const dt = text.match(
    /(\d{4})[-/.](\d{2})[-/.](\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?/,
  );
  if (dt) {
    const [, y, mo, d, hh = '00', mm = '00', ss = '00'] = dt;
    return `${y}-${mo}-${d} ${hh}:${mm}:${ss}`;
  }

  const rel = text.match(/(今天|昨日)\s*(\d{2}):(\d{2})/);
  if (rel) {
    const now = new Date();
    const base = rel[1] === '昨日' ? new Date(now.getTime() - 86400000) : now;
    return `${ymd(base)} ${rel[2]}:${rel[3]}:00`;
  }

  return null;
}
