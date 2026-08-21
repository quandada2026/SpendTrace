package com.example.autoledger.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.autoledger.data.LedgerEntry
import com.example.autoledger.ocr.OcrEngineProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ManualEntryDialog(
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
internal fun EditDialog(entry: LedgerEntry, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (LedgerEntry) -> Unit) {
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
internal fun SettingsTab(context: Context) {
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
