package com.lyx.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Muted = Color(0xFF8F9AAA)
private val Accent = Color(0xFF75DFCE)
private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val SecondsFormat = DateTimeFormatter.ofPattern("ss")
private val DateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日  EEEE", Locale.SIMPLIFIED_CHINESE)
private val PanelDateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)
private val WeekdayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.SIMPLIFIED_CHINESE)

@Composable
fun ClockApp(metrics: Metrics?, endpoint: String, status: String, onSave: (String) -> Unit) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(showControls, settings) {
        if (showControls && !settings) {
            delay(5000)
            showControls = false
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color.Black, surface = Color(0xFF151B22))) {
        Box(Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { showControls = true }) }
            .displayCutoutPadding()) {
            if (metrics == null) {
                ClockFace(Modifier.fillMaxSize(), large = true)
            } else {
                PerformancePanel(metrics)
            }
            if (showControls) {
                TextButton(onClick = { settings = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Text("设置", color = Muted)
                }
            }
        }
        if (settings) {
            EndpointSettings(endpoint, status, onDismiss = { settings = false }, onSave = {
                onSave(it)
                settings = false
                showControls = true
            })
        }
    }
}

@Composable
private fun ClockFace(modifier: Modifier, large: Boolean) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDateTime.now()
                delay(1000 - System.currentTimeMillis() % 1000)
            }
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fontScale = LocalDensity.current.fontScale
        val size = if (large) minOf(maxWidth.value / 4.7f, maxHeight.value / 2.15f) / fontScale
            else minOf(maxWidth.value / 3.8f, maxHeight.value / 3f) / fontScale
        val panelDateSize = minOf(22f, maxWidth.value / 14f) / fontScale
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (large) {
                Text(now.format(DateFormat), color = Muted, fontSize = (size * .18f).sp,
                    modifier = Modifier.padding(bottom = 12.dp))
            } else {
                Text(now.format(PanelDateFormat), color = Muted,
                    fontSize = panelDateSize.sp,
                    maxLines = 1, modifier = Modifier.padding(bottom = 20.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(now.format(ClockFormat), color = Color(0xFFF1F5F9), fontFamily = FontFamily.Monospace,
                    fontSize = size.sp, fontWeight = FontWeight.Light, lineHeight = size.sp)
                Text(now.format(SecondsFormat), color = if (large) Accent else Muted,
                    fontFamily = FontFamily.Monospace, fontSize = (size * .34f).sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = (size * .13f).dp))
            }
            if (!large) {
                Text(now.format(WeekdayFormat), color = Accent, fontSize = (20f / fontScale).sp,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun PerformancePanel(metrics: Metrics) {
    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(.60f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                Box(Modifier.size(6.dp).background(Accent, RoundedCornerShape(3.dp)))
                Text(metrics.hostname, color = Muted, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            MetricRow("CPU", metrics.cpu, "处理器使用率", Accent, Modifier.weight(1f))
            MetricRow("内存", metrics.memory,
                capacity(metrics.usedGb, metrics.totalGb, "GB"), Color(0xFF92B7FF), Modifier.weight(1f))
            MetricRow("GPU", metrics.gpu?.usage,
                metrics.gpu?.let { gpu ->
                    if (gpu.usedMb != null && gpu.totalMb != null) {
                        "显存 " + capacity(gpu.usedMb / 1024, gpu.totalMb / 1024, "GB")
                    } else gpu.vramPercent?.let { "显存 ${decimal(it)}%" } ?: "显存不可用"
                } ?: "显卡不可用", Color(0xFFC2A3FF), Modifier.weight(1f))
        }
        Spacer(Modifier.width(20.dp))
        Box(Modifier.width(1.dp).fillMaxHeight(.65f).background(Color(0xFF222A33)))
        Spacer(Modifier.width(20.dp))
        ClockFace(Modifier.weight(.40f).fillMaxHeight(), large = false)
    }
}

private fun decimal(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun capacity(used: Double?, total: Double?, unit: String): String =
    if (used != null && total != null) "${decimal(used)} / ${decimal(total)} $unit" else "容量不可用"

@Composable
private fun MetricRow(label: String, value: Double?, detail: String, tint: Color, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()
        .background(Color(0xFF10151B), RoundedCornerShape(14.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        val scale = (maxWidth.value / 440f).coerceIn(.70f, 1.1f)
        val fontScale = LocalDensity.current.fontScale
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
            Text(label, color = tint, fontSize = (20 * scale / fontScale).sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.width((50 * scale).dp), maxLines = 1)
            LinearProgressIndicator(progress = { ((value ?: 0.0) / 100).toFloat() },
                modifier = Modifier.weight(1f).height(16.dp), color = tint,
                trackColor = Color(0xFF28303B), drawStopIndicator = {})
            Text(value?.let { "${decimal(it)}%" } ?: "—", color = Color.White,
                fontSize = (22 * scale / fontScale).sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.width((84 * scale).dp), maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(detail, color = Muted, fontSize = (14 * scale / fontScale).sp,
                modifier = Modifier.width((142 * scale).dp), maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun EndpointSettings(endpoint: String, status: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(endpoint) }
    val valid = validEndpoint(draft.trim())
    AlertDialog(onDismissRequest = onDismiss, title = { Text("连接设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("API 地址") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { Text(if (valid) "支持 HTTP / HTTPS" else "请输入有效的 HTTP / HTTPS 地址") })
                Text(status, fontSize = 12.sp, color = Muted)
                Text("在线每秒刷新，离线每 30 秒检测一次。点击屏幕可显示设置入口。", fontSize = 12.sp, color = Muted)
                TextButton(onClick = { draft = DEFAULT_ENDPOINT }) { Text("恢复默认地址") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft.trim()) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
