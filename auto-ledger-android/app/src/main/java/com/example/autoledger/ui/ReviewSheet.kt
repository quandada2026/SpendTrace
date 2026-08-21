package com.example.autoledger.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.autoledger.ReviewDraft
import com.example.autoledger.TradeType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * P0 人工复核闸门：OCR 草稿必须在此确认（或丢弃）后才写库。
 * 金额/商户/方向/时间/分类全部可编辑，候选金额与候选商户一键切换。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReviewSheet(
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
