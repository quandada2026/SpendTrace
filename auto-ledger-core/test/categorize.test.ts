import { test } from 'node:test';
import assert from 'node:assert/strict';
import { categorize } from '../src/categorize/index.js';

test('已知商户映射到分类', () => {
  assert.equal(categorize('星巴克咖啡(天河城店)'), '餐饮');
  assert.equal(categorize('滴滴出行'), '交通');
  assert.equal(categorize('京东商城'), '购物');
  assert.equal(categorize('腾讯视频会员'), '娱乐');
});

test('未知商户 / 空值 -> 其他', () => {
  assert.equal(categorize('某不知名公司'), '其他');
  assert.equal(categorize(null), '其他');
});
