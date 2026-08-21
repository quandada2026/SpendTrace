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

test('扩充关键词命中', () => {
  assert.equal(categorize('元初食品(厦门思明店)'), '购物');
  assert.equal(categorize('叮咚买菜'), '购物');
  assert.equal(categorize('蜜雪冰城'), '餐饮');
  assert.equal(categorize('海底捞火锅'), '餐饮');
  assert.equal(categorize('高德打车'), '交通');
  assert.equal(categorize('贝壳找房'), '居家');
  assert.equal(categorize('老百姓大药房'), '医疗');
  assert.equal(categorize('bilibili大会员'), '娱乐');
  assert.equal(categorize('学而思培优'), '教育');
  assert.equal(categorize('微信红包'), '人情');
});
