package com.example.autoledger.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 首次安装使用说明引导页。
 *
 * 顶部标题固定为「钱迹LOVE使用说明」，内容 3 页横滑：
 *  ① 核心功能（付款截图 → 自动记账）
 *  ② 必要授权（监听 / 读取图片 / 通知）
 *  ③ 截图方式建议（电源键+音量下 / 三指下滑 / 悬浮球·系统级）
 * 底部：跳过 / 下一页（末页为「开始使用」）+ 3 点指示器。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            // 顶部固定标题
            Text(
                "钱迹LOVE使用说明",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                "付款，截个图，就记好了",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // 3 页横滑内容
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> OnboardPage(
                        emoji = "📸",
                        emojiLabel = "付款截图",
                        title = "自动识别，一键入账",
                        bullets = listOf(
                            "端侧 OCR 识别截图金额与商户",
                            "100% 本地识别，数据不出手机",
                            "复核闸门：确认后才写入账本",
                        ),
                    )
                    1 -> OnboardPage(
                        emoji = "🔐",
                        emojiLabel = "必要授权",
                        title = "一次性授权，隐私安全",
                        bullets = listOf(
                            "「读取图片」：识别截图（必须）",
                            "「通知」：后台常驻与记账提示",
                            "「截图监听」：在 App 内一键开启",
                        ),
                    )
                    else -> OnboardPage(
                        emoji = "📱",
                        emojiLabel = "截图方式",
                        title = "怎么触发记账",
                        bullets = listOf(
                            "电源键 + 音量下键（最通用）",
                            "三指下滑（部分机型支持）",
                            "悬浮球 / 快捷手势（系统级设置）",
                        ),
                    )
                }
            }

            // 3 点指示器
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .size(if (pagerState.currentPage == i) 18.dp else 6.dp, 6.dp)
                            .background(
                                color = if (pagerState.currentPage == i) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 底部操作：跳过 | 下一页/开始使用
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onFinish) {
                    Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val isLast = pagerState.currentPage == 2
                Button(
                    onClick = {
                        if (isLast) onFinish()
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(44.dp).width(120.dp),
                ) {
                    Text(if (isLast) "开始使用" else "下一页")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 单页内容：大图标 + 标题 + 要点列表（卡片）。 */
@Composable
private fun OnboardPage(
    emoji: String,
    emojiLabel: String,
    title: String,
    bullets: List<String>,
) {
    Column(
        Modifier.fillMaxSize().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 图标圆
        Box(
            Modifier.size(88.dp).background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 38.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            emojiLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bullets.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("·", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(b, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
