package com.lyx.clock

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        val preferences = getSharedPreferences("clock_settings", MODE_PRIVATE)
        setContent {
            var endpoint by remember { mutableStateOf(preferences.getString("endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT) }
            var metrics by remember { mutableStateOf<Metrics?>(null) }
            var status by remember { mutableStateOf("正在检测 API 服务器…") }
            LaunchedEffect(endpoint) {
                metrics = null
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    metrics = null
                    val policy = MonitorPolicy()
                    while (isActive) {
                        val started = SystemClock.elapsedRealtime()
                        try {
                            metrics = fetchMetrics(endpoint)
                            policy.record(true)
                            status = "已连接 · 每秒刷新"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            metrics = null
                            policy.record(false)
                            status = "连接失败，30 秒后重试 · ${error.localizedMessage ?: "网络不可用"}"
                        }
                        val wait = if (policy.online) {
                            (policy.delayMillis - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1)
                        } else policy.delayMillis
                        delay(wait)
                    }
                }
            }
            ClockApp(metrics, endpoint, status) { newEndpoint ->
                preferences.edit().putString("endpoint", newEndpoint).apply()
                endpoint = newEndpoint
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
