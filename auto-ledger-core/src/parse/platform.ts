import type { Platform } from '../types.js';

/** 根据截图文本识别支付平台。 */
export function detectPlatform(text: string): Platform {
  if (/微信|WeChat|wechat/i.test(text)) return 'wechat';
  if (/支付宝|Alipay|alipay/i.test(text)) return 'alipay';
  if (/银行/.test(text) && /(交易|扣款|付款|消费)/.test(text)) return 'bank';
  if (/银行/.test(text)) return 'bank';
  return 'unknown';
}
