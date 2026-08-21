package com.example.autoledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autoledger.Categories
import com.example.autoledger.data.LedgerEntry
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

/** 统计 Tab 内部二级路由（点分类 / 点日历 → 显示相应账目列表）。 */
internal sealed interface StatsScreen {
    data object Overview : StatsScreen
    data class CategoryEntries(val category: String) : StatsScreen
    data class DayEntries(val date: LocalDate) : StatsScreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsTab(
    screen: StatsScreen,
    expenseSum: Double?,
    incomeSum: Double?,
    expenseTotals: List<com.example.autoledger.data.CategoryTotal>,
    incomeTotals: List<com.example.autoledger.data.CategoryTotal>,
    vm: LedgerViewModel,
    onCategoryClick: (String) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    onAdd: (LocalDate) -> Unit,
) {
    when (screen) {
        StatsScreen.Overview -> OverviewStats(
            expenseSum = expenseSum,
            incomeSum = incomeSum,
            expenseTotals = expenseTotals,
            vm = vm,
            onCategoryClick = onCategoryClick,
            onDayClick = onDayClick,
        )
        is StatsScreen.CategoryEntries -> {
            val list by remember(screen.category) { vm.entriesByCategory(screen.category, 0) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
            CategoryEntriesScreen(
                category = screen.category,
                list = list,
                onBack = onBack,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
        is StatsScreen.DayEntries -> {
            val list by remember(screen.date) { vm.entriesOnDay(screen.date) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
            DayEntriesScreen(
                date = screen.date,
                list = list,
                onBack = onBack,
                onEdit = onEdit,
                onDelete = onDelete,
                onAdd = onAdd,
            )
        }
    }
}

/**
 * a 总览 + b 时间维度 + c 日历 + d 支出分类，按用户要求的自上而下顺序排列。
 * 收入不参与 d 分类展开（用户明确："收入不用分类"）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewStats(
    expenseSum: Double?,
    incomeSum: Double?,
    expenseTotals: List<com.example.autoledger.data.CategoryTotal>,
    vm: LedgerViewModel,
    onCategoryClick: (String) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val expense = expenseSum ?: 0.0
    val income = incomeSum ?: 0.0
    val balance = income - expense
    // 全年聚合（跟随所选月份所在年份）
    val yearExpenseSum by vm.yearExpenseSum.collectAsStateWithLifecycle()
    val yearIncomeSum by vm.yearIncomeSum.collectAsStateWithLifecycle()
    val yExpense = yearExpenseSum ?: 0.0
    val yIncome = yearIncomeSum ?: 0.0
    val yBalance = yIncome - yExpense

    // 整页统一日期源：日历所选月份（默认本月），翻月时汇总/分类/热力全部联动
    val selectedMonth by vm.selectedMonth.collectAsStateWithLifecycle()
    val dailyNet by vm.dailyNetForMonth(selectedMonth).collectAsStateWithLifecycle(initialValue = emptyMap())

    // 模块标题统一靛蓝（杂志主题），不再随机取色
    val monthLabel = "${selectedMonth.year}年${selectedMonth.monthValue}月"

    LazyColumn(Modifier.fillMaxSize()) {
        // a + b：所选月份收支总览（本月 + 全年 双列）
        item {
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${selectedMonth.year}年收支", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    // 表头
                    Row(Modifier.fillMaxWidth()) {
                        Text("", Modifier.weight(0.8f))
                        Text("本月·${selectedMonth.monthValue}月", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("全年", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    // 支出
                    Row(Modifier.fillMaxWidth()) {
                        Text("支出", Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium)
                        Text("¥%.2f".format(expense), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
                        Text("¥%.2f".format(yExpense), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
                    }
                    // 收入
                    Row(Modifier.fillMaxWidth()) {
                        Text("收入", Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium)
                        Text("¥%.2f".format(income), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium, color = Color(0xFF43A047))
                        Text("¥%.2f".format(yIncome), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium, color = Color(0xFF43A047))
                    }
                    // 分隔线
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    // 结余
                    Row(Modifier.fillMaxWidth()) {
                        Text("结余", Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "¥%.2f".format(balance),
                            Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (balance >= 0) Color(0xFF43A047) else Color(0xFFE53935),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "¥%.2f".format(yBalance),
                            Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (yBalance >= 0) Color(0xFF43A047) else Color(0xFFE53935),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // c：所选月份每日收支统计图（日历热力图）
        item {
            CalendarHeatmap(
                yearMonth = selectedMonth,
                dailyNet = dailyNet,
                onDayClick = onDayClick,
                onPrevMonth = { vm.selectedMonth.value = selectedMonth.minusMonths(1) },
                onNextMonth = { vm.selectedMonth.value = selectedMonth.plusMonths(1) },
                onToday = { vm.selectedMonth.value = YearMonth.now() },
            )
        }

        // d：所选月份支出分类（3 层钻取的一级列表）
        item {
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${monthLabel}支出分类",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "点击分类查看明细（分类 → 账目列表 → 单条账目）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (expenseTotals.isEmpty()) {
                        Text(
                            "${monthLabel}暂无支出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        expenseTotals.sortedByDescending { it.total }.forEach { ct ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onCategoryClick(ct.category) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryBadge(ct.category)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "¥%.2f".format(ct.total),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "%.0f%%".format(if (expense > 0) ct.total * 100 / expense else 0.0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 底部留白，避免最后一卡被 FAB 遮挡
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * c 本月每日净结余日历：7×6 格子，每格显示「日期小字 + 放大净结余数字」。
 * 净结余 = 当日收入 − 当日支出；负数红、正数绿、0 不显示数字（只留日期）。点击格子回到 DayEntries 二级。
 */
@Composable
private fun CalendarHeatmap(
    yearMonth: YearMonth,
    dailyNet: Map<LocalDate, Double>,
    onDayClick: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
) {
    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDowOffset = firstDay.dayOfWeek.value - 1

    val cells: List<LocalDate?> = buildList {
        repeat(firstDowOffset) { add(null) }
        for (d in 1..daysInMonth) add(yearMonth.atDay(d))
        while (size < 42) add(null)
    }
    val today = LocalDate.now()
    val green = Color(0xFF43A047)
    val red = Color(0xFFE53935)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = MaterialTheme.colorScheme.primary

    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("每日收支（净结余）", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = titleColor)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevMonth) {
                    Text("‹ 上月", style = MaterialTheme.typography.labelMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(yearMonth.toString(), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
                    TextButton(onClick = onToday) {
                        Text("回到本月", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onNextMonth) {
                    Text("下月 ›", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { dow ->
                    Text(
                        dow,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        if (d == null) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val net = dailyNet[d] ?: 0.0
                            val isToday = d == today
                            val numColor = when {
                                net > 0 -> green
                                net < 0 -> red
                                else -> neutral
                            }
                            val bg = if (net == 0.0) Color.LightGray.copy(alpha = 0.15f)
                                      else numColor.copy(alpha = 0.12f)
                            Box(
                                Modifier.weight(1f).aspectRatio(1f)
                                    .padding(2.dp)
                                    .background(bg, RoundedCornerShape(4.dp))
                                    .let { if (isToday) it.border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)) else it }
                                    .clickable { onDayClick(d) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${d.dayOfMonth}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (net != 0.0) {
                                        Text(
                                            "%.0f".format(net),
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = numColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** d 二级：分类账目列表 + 顶部"← 返回"与汇总信息。复用 EntryList（带 10 条分页）。 */
@Composable
private fun CategoryEntriesScreen(
    category: String,
    list: List<LedgerEntry>,
    onBack: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            CategoryBadge(category)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${list.size} 笔 · ¥%.2f".format(list.sumOf { it.amount ?: 0.0 }), style = MaterialTheme.typography.bodySmall)
            }
        }
        EntryList(list = list, onEdit = onEdit, onDelete = onDelete)
    }
}

/** c 二级：日历某日账目列表（含收入 + 支出，与 period 解耦，仅按日过滤）。 */
@Composable
internal fun DayEntriesScreen(
    date: LocalDate,
    list: List<LedgerEntry>,
    onBack: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    onAdd: (LocalDate) -> Unit,
) {
    val density = LocalDensity.current
    val backThresholdPx = with(density) { 40.dp.toPx() }
    // 屏幕左 100dp 边缘区（绕开 Android 10+ 系统级边缘返回手势的前 ~20dp）
    val edgeZonePx = with(density) { 100.dp.toPx() }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 仅在屏幕左 edgeZonePx 内的触摸启动检测（右侧不会误触）
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > edgeZonePx) return@awaitEachGesture
                    var totalDx = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        totalDx += change.position.x - change.previousPosition.x
                    }
                    if (totalDx > backThresholdPx) onBack()
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回（右滑屏幕也可返回）",
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(date.toString(), style = MaterialTheme.typography.titleMedium)
                Text("${list.size} 笔 · 支出 ¥%.2f".format(list.filter { it.direction == 0 }.sumOf { it.amount ?: 0.0 }), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onAdd(date) }) {
                Icon(Icons.Filled.Add, contentDescription = "补记")
            }
        }
        Text(
            "点击任意一笔可修正或删除",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
        )
        EntryList(list = list, onEdit = onEdit, onDelete = onDelete)
    }
}

/** 分类标签色（浅色主题下用深色系，文字白色保证可读）。 */
private fun categoryColor(category: String): Color = when (category) {
    Categories.餐饮 -> Color(0xFFE53935)
    Categories.购物 -> Color(0xFFFB8C00)
    Categories.交通 -> Color(0xFF1E88E5)
    Categories.休闲 -> Color(0xFF8E24AA)
    Categories.医疗 -> Color(0xFFD81B60)
    Categories.学习 -> Color(0xFF43A047)
    Categories.人情支出 -> Color(0xFF6D4C41)
    Categories.住房 -> Color(0xFF00897B)
    Categories.工资 -> Color(0xFF039BE5)
    Categories.转账 -> Color(0xFF5E35B1)
    Categories.其他收入 -> Color(0xFF00ACC1)
    else -> Color(0xFF78909C)   // 其他 / 未知
}

/** 分类标签：置顶显示，一眼看清钱花在哪类。 */
@Composable
private fun CategoryBadge(category: String) {
    Surface(
        color = categoryColor(category),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            category,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** 通用账目列表：分类置顶、金额右对齐、点单条修正/删除，10 条分页。 */
@Composable
internal fun EntryList(
    list: List<LedgerEntry>,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    highlightReview: Boolean = false,
    pageSize: Int = 10,
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无记录")
        }
        return
    }

    // 分页：每页最多 pageSize 条，新记录在最前（第一页）
    var page by remember { mutableStateOf(0) }
    val totalPages = max(1, (list.size + pageSize - 1) / pageSize)
    LaunchedEffect(list.size) {
        if (page >= totalPages) page = totalPages - 1
    }
    val pageList = list.drop(page * pageSize).take(pageSize)

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(pageList, key = { it.id }) { e ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable { onEdit(e) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            // 分类置顶（与商户对调：商户移到第二行）
                            CategoryBadge(e.category)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${e.merchant ?: "未知商户"}  ·  ${e.time ?: "无时间"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (e.needsReview && highlightReview) {
                                Text("⚠ 待核对（金额未识别）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            if (e.amount != null) "¥%.2f".format(e.amount) else "—",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { onEdit(e) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "修正")
                        }
                        IconButton(onClick = { onDelete(e) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        // 分页栏（留 96dp 底部空间，避免被右下角的「+」悬浮按钮压住）
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("共 ${list.size} 条", style = MaterialTheme.typography.bodySmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("上一页") }
                Text("${page + 1}/$totalPages", style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = { if (page < totalPages - 1) page++ },
                    enabled = page < totalPages - 1,
                ) { Text("下一页") }
            }
        }
    }
}
