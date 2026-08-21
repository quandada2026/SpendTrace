package com.example.autoledger.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autoledger.Categories
import com.example.autoledger.ReviewDraft
import com.example.autoledger.TradeType
import com.example.autoledger.data.LedgerEntry
import com.example.autoledger.ocr.OcrEngineProvider
import com.example.autoledger.watcher.ScreenshotService
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max

private val CATEGORY_OPTIONS = listOf(
    Categories.餐饮, Categories.购物, Categories.交通, Categories.休闲,
    Categories.医疗, Categories.学习, Categories.人情支出, Categories.住房, Categories.其他,
)
private val INCOME_OPTIONS = listOf(
    Categories.工资, Categories.转账, Categories.其他收入,
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = IndigoMagazineColors, typography = IndigoMagazineTypography) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: LedgerViewModel = viewModel()) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var watching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }

    val entries by vm.entries.collectAsStateWithLifecycle()
    val review by vm.needsReview.collectAsStateWithLifecycle()
    val expenseSum by vm.expenseSum.collectAsStateWithLifecycle()
    val expenseTotals by vm.expenseTotals.collectAsStateWithLifecycle()
    val incomeSum by vm.incomeSum.collectAsStateWithLifecycle()
    val incomeTotals by vm.incomeTotals.collectAsStateWithLifecycle()
    // 日历热力图改为按所选月份按需查询（见 OverviewStats 的 selectedMonth + vm.dailyNetForMonth）
    var showManual by remember { mutableStateOf(false) }
    var manualDate by remember { mutableStateOf<LocalDate?>(null) }

    // 待复核草稿队列（Service 自动监听 + 手动上传都会入队），弹复核页等用户确认
    val reviewQueue by ReviewBus.queue.collectAsStateWithLifecycle()
    val currentReview = reviewQueue.firstOrNull()

    // 统计 Tab 三级钻取状态：概览 / 分类账目 / 日账目。
    // 二级页面全屏覆盖，左上角"返回"回到 Overview。
    var statsScreen by remember { mutableStateOf<StatsScreen>(StatsScreen.Overview) }

    // 手动上传结果提示（成功 / 已忽略 / 识别失败）
    val processMsg by vm.processMessage.collectAsStateWithLifecycle()
    LaunchedEffect(processMsg) {
        if (processMsg != null) {
            Toast.makeText(context, processMsg, Toast.LENGTH_LONG).show()
            vm.clearProcessMessage()
        }
    }

    // 权限引导
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {}
    LaunchedEffect(Unit) {
        val imgPerm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val needed = mutableListOf(imgPerm)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    // 手动上传（支持一次多选多张截图；逐张分析后入复核队列，用户依次核对）
    var pendingUploadDate by remember { mutableStateOf<LocalDate?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris: List<Uri>? -> uris?.forEach { vm.processManualUri(it, pendingUploadDate) } }

    val watchIntent = remember { Intent(context, ScreenshotService::class.java) }

    // FAB 仅在"统计页 + Overview 一级"显示（手动记账从最常见入口可达）。
    val showFab = tab == 0 && statsScreen is StatsScreen.Overview

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动记账") },
                actions = {
                    Button(onClick = {
                        if (watching) {
                            context.stopService(watchIntent)
                            watching = false
                        } else {
                            ContextCompat.startForegroundService(context, watchIntent)
                            watching = true
                        }
                    }) {
                        Text(if (watching) "停止监听" else "开启监听")
                    }
                    IconButton(onClick = {
                        pendingUploadDate = (statsScreen as? StatsScreen.DayEntries)?.date
                        pickLauncher.launch("image/*")
                    }) {
                        Icon(Icons.Filled.Upload, contentDescription = "手动上传截图")
                    }
                },
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { manualDate = null; showManual = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "手动记账")
                }
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; statsScreen = StatsScreen.Overview }, text = { Text("统计", maxLines = 1, softWrap = false) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("账本 ${entries.size}", maxLines = 1, softWrap = false) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("待核对 ${review.size}", maxLines = 1, softWrap = false) })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("设置", maxLines = 1, softWrap = false) })
            }
            when (tab) {
                0 -> StatsTab(
                    screen = statsScreen,
                    expenseSum = expenseSum,
                    incomeSum = incomeSum,
                    expenseTotals = expenseTotals,
                    incomeTotals = incomeTotals,
                    vm = vm,
                    onCategoryClick = { statsScreen = StatsScreen.CategoryEntries(it) },
                    onDayClick = { statsScreen = StatsScreen.DayEntries(it) },
                    onBack = { statsScreen = StatsScreen.Overview },
                    onEdit = { editing = it },
                    onDelete = { vm.delete(it) },
                    onAdd = { d -> manualDate = d; showManual = true },
                )
                1 -> EntryList(entries, onEdit = { editing = it }, onDelete = { vm.delete(it) })
                2 -> EntryList(review, onEdit = { editing = it }, onDelete = { vm.delete(it) }, highlightReview = true)
                3 -> SettingsTab(context)
            }
        }
    }

    if (showManual) {
        ManualEntryDialog(
            initialDate = manualDate ?: LocalDate.now(),
            onDismiss = { showManual = false },
            onSave = { amount, category, direction, merchant, note, date ->
                vm.insertManual(amount, category, direction, merchant, note, date)
                showManual = false
            },
        )
    }

    editing?.let { entry ->
        EditDialog(
            entry = entry,
            onDismiss = { editing = null },
            onDelete = {
                vm.delete(entry)
                editing = null
            },
        ) { updated ->
            vm.update(updated)
            editing = null
        }
    }

    // P0 复核闸门：有草稿必须先确认/丢弃，确认后才 commit 写库
    currentReview?.let { draft ->
        ReviewSheet(
            draft = draft,
            onConfirm = { edited -> vm.commitReview(edited) },
            onDiscard = { vm.discardReview(draft.id) },
            onManualEntry = {
                vm.discardReview(draft.id)
                manualDate = null
                showManual = true
            },
        )
    }
}

/** 统计 Tab 内部二级路由（点分类 / 点日历 → 显示相应账目列表）。 */
private sealed interface StatsScreen {
    data object Overview : StatsScreen
    data class CategoryEntries(val category: String) : StatsScreen
    data class DayEntries(val date: LocalDate) : StatsScreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsTab(
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

    // 整页统一日期源：日历所选月份（默认本月），翻月时汇总/分类/热力全部联动
    val selectedMonth by vm.selectedMonth.collectAsStateWithLifecycle()
    val dailyNet by vm.dailyNetForMonth(selectedMonth).collectAsStateWithLifecycle(initialValue = emptyMap())

    // 模块标题统一靛蓝（杂志主题），不再随机取色
    val monthLabel = "${selectedMonth.year}年${selectedMonth.monthValue}月"

    LazyColumn(Modifier.fillMaxSize()) {
        // a + b：所选月份收支总览
        item {
            Card(Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${monthLabel}收支", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("支出", style = MaterialTheme.typography.bodyMedium)
                        Text("¥%.2f".format(expense), style = MaterialTheme.typography.titleMedium, color = Color(0xFFE53935))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("收入", style = MaterialTheme.typography.bodyMedium)
                        Text("¥%.2f".format(income), style = MaterialTheme.typography.titleMedium, color = Color(0xFF43A047))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "结余",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "¥%.2f".format(balance),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (balance >= 0) Color(0xFF43A047) else Color(0xFFE53935),
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
private fun DayEntriesScreen(
    date: LocalDate,
    list: List<LedgerEntry>,
    onBack: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    onAdd: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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

@Composable
private fun EntryList(
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ManualEntryDialog(
    initialDate: LocalDate = LocalDate.now(),
    onDismiss: () -> Unit,
    onSave: (amount: Double, category: String, direction: Int, merchant: String?, note: String?, date: LocalDate) -> Unit,
) {
    var direction by remember { mutableStateOf(0) } // 0=支出 1=收入
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    val cat = category
                    if (amt != null && amt > 0 && cat != null) {
                        onSave(amt, cat, direction, merchant, note, selectedDate)
                    }
                },
                enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && category != null,
            ) { Text("保存") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("取消") } },
        title = { Text("手动记账") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabRow(selectedTabIndex = direction) {
                    Tab(selected = direction == 0, onClick = { direction = 0 }, text = { Text("支出") })
                    Tab(selected = direction == 1, onClick = { direction = 1 }, text = { Text("收入") })
                }
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("金额（必填）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                // 记账日期（可改，用于给历史某天补记）
                OutlinedTextField(
                    value = selectedDate.toString(),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("记账日期") },
                    trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                )
                if (showDatePicker) {
                    // 内联展开，避免在 AlertDialog 里再套 Dialog（dialog-in-dialog 触摸被拦截）
                    val pickerState = rememberDatePickerState(
                        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    )
                    DatePicker(state = pickerState, showModeToggle = false)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                            showDatePicker = false
                        }) { Text("确定") }
                    }
                }
                Text("分类（必填）", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = if (direction == 0) CATEGORY_OPTIONS else INCOME_OPTIONS
                    options.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) },
                        )
                    }
                }
                OutlinedTextField(merchant, { merchant = it }, label = { Text("商户（可选）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDialog(entry: LedgerEntry, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (LedgerEntry) -> Unit) {
    var merchant by remember { mutableStateOf(entry.merchant ?: "") }
    var amount by remember { mutableStateOf(entry.amount?.toString() ?: "") }
    var time by remember { mutableStateOf(entry.time ?: "") }
    var category by remember { mutableStateOf(entry.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                val t = if (time.isBlank()) null else time
                onSave(
                    entry.copy(
                        merchant = merchant.ifBlank { null },
                        amount = amt,
                        time = t,
                        category = category,
                        needsReview = amt == null,
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("取消") } },
        title = { Text("修正账目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.rawText.isNotBlank()) {
                    Text("原始识别：\n${entry.rawText.take(400)}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(merchant, { merchant = it }, label = { Text("商户") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(time, { time = it }, label = { Text("时间 YYYY-MM-DD HH:MM:SS") }, modifier = Modifier.fillMaxWidth())
                Text("分类", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(CATEGORY_OPTIONS) { c ->
                        FilterChip(
                            selected = c == category,
                            onClick = { category = c },
                            label = { Text(c) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("删除此条记录")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(context: Context) {
    val prefs = remember { context.getSharedPreferences("autoledger", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(prefs.getString("ocr_mode", "local") ?: "local") }
    var endpoint by remember { mutableStateOf(prefs.getString("cloud_endpoint", "") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("cloud_api_key", "") ?: "") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("OCR 引擎", style = MaterialTheme.typography.titleMedium)
        Text("默认「端侧 ML Kit」：免费、离线、隐私最优。需要更高中文准确率时切「云端」并填入厂商配置。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "local", onClick = { mode = "local" }, label = { Text("端侧(local)") })
            FilterChip(selected = mode == "cloud", onClick = { mode = "cloud" }, label = { Text("云端(cloud)") })
        }
        if (mode == "cloud") {
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("云端 OCR 地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            if (mode == "cloud") {
                OcrEngineProvider.saveCloudConfig(context, endpoint, apiKey)
            } else {
                OcrEngineProvider.useLocal(context)
            }
        }) { Text("保存设置") }
        Text("使用说明：开启「监听」后，系统截图一生成即自动记账；无双击截图功能的手机用右上角「上传」按钮手动选图兜底。", style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * P0 人工复核闸门：OCR 草稿必须在此确认（或丢弃）后才写库。
 * 金额/商户/方向/时间/分类全部可编辑，候选金额与候选商户一键切换。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReviewSheet(
    draft: ReviewDraft,
    onConfirm: (ReviewDraft) -> Unit,
    onDiscard: () -> Unit,
    onManualEntry: () -> Unit,
) {
    var amountText by remember(draft.id) {
        mutableStateOf(draft.suggestMoney?.let { "%.2f".format(kotlin.math.abs(it)) } ?: "")
    }
    var merchant by remember(draft.id) { mutableStateOf(draft.suggestMerchant ?: "") }
    // 交易日期：OCR 日期 > 浏览日期上下文(contextDate) > 今天；时刻保留 OCR 识别到的，否则取当前时刻
    val ocrDate = remember(draft.id) {
        draft.tradeTime?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
    }
    val timeSuffix = remember(draft.id) {
        draft.tradeTime?.let { if (it.length >= 19) it.substring(11, 19) else null }
            ?: run {
                val n = java.time.LocalDateTime.now()
                "%02d:%02d:%02d".format(n.hour, n.minute, n.second)
            }
    }
    var selectedDate by remember(draft.id) { mutableStateOf(ocrDate ?: draft.contextDate ?: LocalDate.now()) }
    // 日期选择改为「内联展开」，不在 AlertDialog 里再套 Dialog（dialog-in-dialog 触摸被拦截，点不动是已知坑）
    var showDatePicker by remember(draft.id) { mutableStateOf(false) }
    // -1=未知（识别没把握，强制人工选 支出/收入）
    var direction by remember(draft.id) {
        mutableStateOf(
            when (draft.tradeType) {
                TradeType.INCOME -> 1
                TradeType.EXPENSE -> 0
                // 金额带负号（财付通/微信支出表示法）默认支出，不强制人工；
                // 否则（正金额或未知）强制人工选方向，绝不瞎猜。
                TradeType.UNKNOWN -> if (draft.suggestMoney != null && draft.suggestMoney < 0) 0 else -1
            },
        )
    }
    var category by remember(draft.id) { mutableStateOf(draft.category) }

    // 金额文本框存绝对值（方向由 direction 决定），故校验 abs>0 而非 >0；
    // 负号是财付通/微信支出表示法，不能因负号判金额无效。
    val amountValid = amountText.toDoubleOrNull()?.let { kotlin.math.abs(it) > 0.001 } == true
    val dirValid = direction == 0 || direction == 1
    val fmt: (Double) -> String = { "%.2f".format(it) }

    AlertDialog(
        onDismissRequest = onDiscard,
        confirmButton = {
            Button(
                onClick = {
                    val edited = draft.copy(
                        suggestMoney = amountText.toDoubleOrNull(),
                        suggestMerchant = merchant.ifBlank { null },
                        tradeTime = "%04d-%02d-%02d %s".format(
                            selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth, timeSuffix,
                        ),
                        tradeType = when (direction) {
                            1 -> TradeType.INCOME
                            0 -> TradeType.EXPENSE
                            else -> TradeType.UNKNOWN
                        },
                        category = category,
                    )
                    onConfirm(edited)
                },
                enabled = amountValid && dirValid,
            ) { Text("确认记账") }
        },
        dismissButton = { Button(onClick = onDiscard) { Text("丢弃") } },
        title = { Text("核对账单") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.imagePath?.let { path ->
                    val bmp = remember(path) { BitmapFactory.decodeFile(path) }
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "原图",
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                        )
                    }
                }
                draft.warningMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (!draft.success) {
                    Text(
                        "OCR 未能识别有效信息。可重新截图上传，或直接手动记账。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onManualEntry) { Text("转手动记账") }
                }
                OutlinedTextField(
                    amountText, { amountText = it },
                    label = { Text(if (draft.suggestMoney == null) "金额（未识别·请手动输入）" else "金额（必填）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draft.candidateMoneyList.size > 1) {
                    Text("候选金额（点选替换）", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        draft.candidateMoneyList.forEach { c ->
                            FilterChip(
                                selected = fmt(amountText.toDoubleOrNull() ?: -1.0) == fmt(kotlin.math.abs(c.value)),
                                onClick = { amountText = fmt(kotlin.math.abs(c.value)) },
                                label = { Text("¥" + fmt(kotlin.math.abs(c.value))) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    merchant, { merchant = it },
                    label = { Text("商户（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draft.merchantCandidates.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        draft.merchantCandidates.forEach { name ->
                            FilterChip(
                                selected = merchant == name,
                                onClick = { merchant = name },
                                label = { Text(name) },
                            )
                        }
                    }
                }
                Text("收支方向（必选）", style = MaterialTheme.typography.labelMedium)
                TabRow(selectedTabIndex = if (direction == -1) 0 else direction) {
                    Tab(selected = direction == 0, onClick = { direction = 0 }, text = { Text("支出") })
                    Tab(selected = direction == 1, onClick = { direction = 1 }, text = { Text("收入") })
                }
                if (!dirValid) {
                    Text("请选择支出或收入", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                // 交易日期（可点选修改：OCR 日期 > 浏览日期 > 今天）
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("交易日期", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { showDatePicker = true }) {
                        Text(selectedDate.toString())
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("（点击修改）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showDatePicker) {
                    // 每次展开用当前 selectedDate 新建 state（避免 remember 复用旧选择导致改了没反应）
                    val pickerState = rememberDatePickerState(
                        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    )
                    DatePicker(state = pickerState, showModeToggle = false)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                            showDatePicker = false
                        }) { Text("确定") }
                    }
                }
                Text("分类（可选，留空自动归类）", style = MaterialTheme.typography.labelMedium)
                val catOptions = if (direction == 1) INCOME_OPTIONS else CATEGORY_OPTIONS
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    catOptions.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) },
                        )
                    }
                }
            }
        },
    )
}
