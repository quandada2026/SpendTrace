package com.example.autoledger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoledger.AutoLedgerApplication
import com.example.autoledger.Platforms
import com.example.autoledger.data.CategoryTotal
import com.example.autoledger.data.DayTotal
import com.example.autoledger.data.LedgerDao
import com.example.autoledger.data.LedgerEntry
import com.example.autoledger.ocr.OcrEngineProvider
import com.example.autoledger.pipeline.LedgerPipeline
import com.example.autoledger.ReviewDraft
import com.example.autoledger.ui.ReviewBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import java.io.File

/** 统计周期。包含「今天」（用户要求 b 增加此维度）。 */
enum class Period { TODAY, WEEK, MONTH, YEAR }

/**
 * 向 UI 暴露账本流与操作（手动记账、修正、删除、周期统计）。
 */
class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: LedgerDao = (application as AutoLedgerApplication).database.dao()

    val entries: StateFlow<List<LedgerEntry>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val needsReview: StateFlow<List<LedgerEntry>> = dao.observeNeedsReview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前统计周期（周/月/年）。 */
    private val period = MutableStateFlow(Period.MONTH)
    val periodState: StateFlow<Period> = period.asStateFlow()

    /** 计算 [start, end) 的日期范围字符串（time 列 'YYYY-MM-DD HH:MM:SS' 字典序即时间序）。 */
    private fun range(p: Period): Pair<String, String> {
        val now = LocalDate.now()
        return when (p) {
            // 仅今天：当天 00:00 ~ 次日 00:00
            Period.TODAY -> now.toString() to now.plusDays(1).toString()
            // 周一 ~ 周日（ISO 周，国内习惯）
            Period.WEEK -> {
                val monday = now.with(DayOfWeek.MONDAY)
                monday.toString() to monday.plusDays(7).toString()
            }
            Period.MONTH -> {
                val first = LocalDate.of(now.year, now.month, 1)
                first.toString() to first.plusMonths(1).toString()
            }
            Period.YEAR -> {
                val first = LocalDate.of(now.year, 1, 1)
                first.toString() to first.plusYears(1).toString()
            }
        }
    }

    /** 当前自然月起止（与 period 解耦，专门用于 c/日历每日统计）。 */
    private fun thisMonthRange(): Pair<String, String> {
        val ym = YearMonth.now()
        return ym.atDay(1).toString() to ym.atEndOfMonth().plusDays(1).toString()
    }

    private fun categoryFlows(direction: Int): StateFlow<List<CategoryTotal>> =
        period.flatMapLatest { p ->
            val (s, e) = range(p)
            dao.rangeCategoryTotals(s, e, direction)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sumFlows(direction: Int): StateFlow<Double?> =
        period.flatMapLatest { p ->
            val (s, e) = range(p)
            dao.rangeTotal(s, e, direction)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val expenseTotals: StateFlow<List<CategoryTotal>> = categoryFlows(0)
    val expenseSum: StateFlow<Double?> = sumFlows(0)
    val incomeTotals: StateFlow<List<CategoryTotal>> = categoryFlows(1)
    val incomeSum: StateFlow<Double?> = sumFlows(1)

    /**
     * 本月每日支出合计（与 period 解耦，专门用于 c/日历染色）。
     * 月初切日靠 app 重启刷新；用户在月初前夜 23:59 跨入新月份不影响日常使用。
     */
    val dailyExpenseMap: StateFlow<Map<LocalDate, Double>> = run {
        val (s, e) = thisMonthRange()
        dao.dailyExpenseTotals(s, e).map { list ->
            list.associate { LocalDate.parse(it.date) to it.total }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 本月每日收入合计（与 period 解耦，用于 c/日历净结余 = 收入 − 支出）。 */
    val dailyIncomeMap: StateFlow<Map<LocalDate, Double>> = run {
        val (s, e) = thisMonthRange()
        dao.dailyIncomeTotals(s, e).map { list ->
            list.associate { LocalDate.parse(it.date) to it.total }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 指定年月的每日支出合计（用于日历翻月热力图，与 period 无关）。 */
    fun dailyExpenseForMonth(ym: YearMonth): StateFlow<Map<LocalDate, Double>> {
        val (s, e) = monthRange(ym)
        return dao.dailyExpenseTotals(s, e).map { list ->
            list.associate { LocalDate.parse(it.date) to it.total }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    /** 指定年月的每日收入合计。 */
    fun dailyIncomeForMonth(ym: YearMonth): StateFlow<Map<LocalDate, Double>> {
        val (s, e) = monthRange(ym)
        return dao.dailyIncomeTotals(s, e).map { list ->
            list.associate { LocalDate.parse(it.date) to it.total }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    /** 指定年月的每日净结余（收入 − 支出），供日历热力图按所选月份展示。 */
    fun dailyNetForMonth(ym: YearMonth): StateFlow<Map<LocalDate, Double>> =
        dailyExpenseForMonth(ym).combine(dailyIncomeForMonth(ym)) { exp, inc ->
            val keys = exp.keys + inc.keys
            keys.associateWith { (inc[it] ?: 0.0) - (exp[it] ?: 0.0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 某年月的 [start, end) 时间范围字符串（用于按月聚合查询）。 */
    private fun monthRange(ym: YearMonth): Pair<String, String> =
        ym.atDay(1).toString() to ym.atEndOfMonth().plusDays(1).toString()

    /** d 三级钻取：当前 period 内该分类账目列表。 */
    fun entriesByCategory(category: String, direction: Int): Flow<List<LedgerEntry>> =
        period.flatMapLatest { p ->
            val (s, e) = range(p)
            dao.observeByCategoryInRange(s, e, category, direction)
        }

    /** c 三级钻取：日历某日的所有账目（不限方向，跟随该日实际记录）。 */
    fun entriesOnDay(date: LocalDate): Flow<List<LedgerEntry>> =
        dao.observeByDay(date.toString())

    fun setPeriod(p: Period) { period.value = p }

    /** 手动记账：不经过 OCR，直接入库（source=manual）。 */
    fun insertManual(amount: Double, category: String, direction: Int, merchant: String?, note: String?, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = LocalDateTime.now()
            val time = "%04d-%02d-%02d %02d:%02d:%02d".format(
                date.year, date.monthValue, date.dayOfMonth, now.hour, now.minute, now.second,
            )
            val entry = LedgerEntry(
                id = UUID.randomUUID().toString(),
                platform = Platforms.UNKNOWN,
                merchant = merchant?.takeIf { it.isNotBlank() } ?: "手动记账",
                amount = amount,
                direction = direction,
                category = category,
                time = time,
                currency = "CNY",
                source = "manual",
                needsReview = false,
                rawText = "手动记账" + (note?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                createdAt = time,
            )
            dao.insert(entry)
        }
    }

    /** 手动上传截图：只做分析，草稿入 ReviewBus 等待用户复核，不直接入库。 */
    fun processManualUri(uri: Uri, presetDate: LocalDate? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val pipeline = LedgerPipeline(dao, OcrEngineProvider.getEngine(getApplication()))
            val draft = pipeline.analyzeUri(getApplication(), uri, "manual", presetDate)
            ReviewBus.offer(draft)
        }
    }

    /** 用户确认复核草稿 → 写库。先出队（防重复点击/快速二次上传残留），再异步 commit。 */
    fun commitReview(draft: ReviewDraft) {
        ReviewBus.remove(draft.id)
        viewModelScope.launch(Dispatchers.IO) {
            val pipeline = LedgerPipeline(dao, OcrEngineProvider.getEngine(getApplication()))
            val entry = pipeline.commit(draft)
            _processMessage.value = if (entry != null) {
                "已记账：${entry.merchant ?: "未知商户"} ¥%.2f".format(entry.amount)
            } else {
                "未能记账（金额缺失或识别失败）"
            }
        }
    }

    /** 用户丢弃草稿：删临时图并从队列移除。 */
    fun discardReview(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ReviewBus.peek(id)?.imagePath?.let { File(it).delete() }
            ReviewBus.remove(id)
        }
    }

    /** 手动上传/识别结果提示（Toast 展示后由 UI 清除）。 */
    private val _processMessage = MutableStateFlow<String?>(null)
    val processMessage: StateFlow<String?> = _processMessage.asStateFlow()

    fun clearProcessMessage() { _processMessage.value = null }

    fun update(entry: LedgerEntry) = viewModelScope.launch(Dispatchers.IO) { dao.update(entry) }

    fun delete(entry: LedgerEntry) = viewModelScope.launch(Dispatchers.IO) { dao.delete(entry) }
}
