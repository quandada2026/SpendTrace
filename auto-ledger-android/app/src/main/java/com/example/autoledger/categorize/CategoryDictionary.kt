package com.example.autoledger.categorize

import com.example.autoledger.Categories

/**
 * 商户关键词 → 分类 字典。新增分类只需在此追加。
 * 与核心引擎 CATEGORY_KEYWORDS 完全一致。
 */
object CategoryDictionary {

    val KEYWORDS: Map<String, List<String>> = mapOf(
        Categories.餐饮 to listOf(
            "星巴克", "瑞幸", "麦当劳", "肯德基", "必胜客", "餐厅", "餐饮", "食堂", "咖啡",
            "奶茶", "美团", "饿了么", "小吃", "饭店", "烘焙", "面包",
        ),
        Categories.交通 to listOf(
            "滴滴", "地铁", "公交", "加油", "中石化", "中石油", "高铁", "12306", "出租车",
            "停车", "网约车", "骑行", "充电",
        ),
        Categories.购物 to listOf(
            "淘宝", "京东", "天猫", "拼多多", "超市", "便利店", "商城", "优衣库", "名创",
            "沃尔玛", "苏宁", "唯品会", "网易严选",
            "华润", "万家", "永辉", "盒马", "山姆", "大润发", "物美", "家乐福", "罗森",
            "711", "7-11", "百果园", "钱大妈", "ole", "好又多", "屈臣氏", "无印良品",
        ),
        Categories.住房 to listOf(
            "水电", "物业", "燃气", "宽带", "房租", "家政", "开锁", "维修", "搬家",
        ),
        Categories.医疗 to listOf(
            "医院", "药店", "诊所", "健康", "体检", "医疗", "大药房", "养生",
        ),
        Categories.休闲 to listOf(
            "视频", "会员", "游戏", "电影", "音乐", "腾讯视频", "爱奇艺", "网易云", "演出", "门票",
        ),
        Categories.学习 to listOf(
            "培训", "课程", "教育", "书店", "图书", "学堂", "网课", "学费",
        ),
        Categories.人情支出 to listOf(
            "红包", "转账", "礼金", "份子", "请客",
        ),
        Categories.其他 to emptyList(),
    )
}
