import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseScreenshot } from '../src/parse/index.js';
import { extractAmount } from '../src/parse/amount.js';
import { extractTime } from '../src/parse/time.js';
import { extractMerchant } from '../src/parse/merchant.js';
import { detectPlatform } from '../src/parse/platform.js';
import type { OcrResult } from '../src/types.js';

const wechat = `微信支付
支付成功
收款方备注：星巴克咖啡(天河城店)
付款方式：零钱
金额 ¥32.00
2026-08-18 09:30:12`;

const alipay = `支付宝
支付成功
商家：麦当劳
付款方式：花呗
-¥25.50
2026-08-18 12:01:05`;

const bank = `招商银行
交易成功
交易金额 ¥1,200.00
对方户名：某某科技有限公司
2026/08/18 18:22:33`;

function ocr(text: string): OcrResult {
  return { text, blocks: [], engine: 'test' };
}

test('wechat: 金额/商户/时间/平台 全中', () => {
  const p = parseScreenshot(ocr(wechat));
  assert.equal(p.amount, 32.0);
  assert.equal(p.merchant, '星巴克咖啡(天河城店)');
  assert.equal(p.time, '2026-08-18 09:30:12');
  assert.equal(p.platform, 'wechat');
  assert.equal(p.needsReview, false);
});

test('alipay: 负号金额忽略符号', () => {
  const p = parseScreenshot(ocr(alipay));
  assert.equal(p.amount, 25.5);
  assert.equal(p.merchant, '麦当劳');
  assert.equal(p.platform, 'alipay');
});

test('bank: 千分位逗号 + 斜杠日期', () => {
  const p = parseScreenshot(ocr(bank));
  assert.equal(p.amount, 1200.0);
  assert.equal(p.merchant, '某某科技有限公司');
  assert.equal(p.time, '2026-08-18 18:22:33');
  assert.equal(p.platform, 'bank');
});

test('amount: 优先选金额关键词上下文', () => {
  const text = `优惠券 -¥5.00\n支付金额 ¥88.00\n余额 ¥1000.00`;
  assert.equal(extractAmount(text), 88.0);
});

test('amount: 全角符号兼容', () => {
  assert.equal(extractAmount('付款 －￥66.60'), 66.6);
});

test('time: 今天/昨日 相对时间', () => {
  const t = extractTime('今天 09:05');
  assert.ok(t && t.endsWith('09:05:00'));
  assert.ok(extractTime('昨日 22:10')!.startsWith('20'));
});

test('merchant: 无标签返回 null', () => {
  assert.equal(extractMerchant('一段没有商户信息的文字'), null);
});

test('platform: 未识别为 unknown', () => {
  assert.equal(detectPlatform('某个不知名收款码'), 'unknown');
});
