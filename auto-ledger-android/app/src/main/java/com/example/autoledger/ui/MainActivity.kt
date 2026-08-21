package com.example.autoledger.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autoledger.Categories
import com.example.autoledger.data.LedgerEntry
import com.example.autoledger.watcher.ScreenshotService
import java.time.LocalDate

/** 支出分类选项（供手动记账 / 修正 / 复核页使用）。 */
internal val CATEGORY_OPTIONS = listOf(
    Categories.餐饮, Categories.购物, Categories.交通, Categories.休闲,
    Categories.医疗, Categories.学习, Categories.人情支出, Categories.住房, Categories.其他,
)
/** 收入分类选项。 */
internal val INCOME_OPTIONS = listOf(
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
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
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
                            androidx.core.content.ContextCompat.startForegroundService(context, watchIntent)
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
