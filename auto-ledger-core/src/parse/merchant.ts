/**
 * 抽取收款方/商户名。
 * 优先匹配「收款方备注：/商家：/商户：/对方户名：/商品：/付款给：」等标签；
 * 兜底匹配「给 XXX 转账/付款」句式。匹配不到返回 null。
 */
export function extractMerchant(text: string): string | null {
  const labelled = text.match(
    /(?:收款方备注|收款方|商家|商户|对方户名|商品|付款给)\s*[：:]\s*([^\n；;]+)/,
  );
  if (labelled) return labelled[1].trim().replace(/[，。\s]+$/, '');

  const give = text.match(/给\s*([^\n：:]+?)\s*(?:的)?(?:转账|付款|支付)/);
  if (give) return give[1].trim();

  return null;
}
