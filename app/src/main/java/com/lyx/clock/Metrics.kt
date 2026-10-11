package com.lyx.clock

import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GpuMetrics(val name: String, val usage: Double?, val vramPercent: Double?, val usedMb: Double?, val totalMb: Double?)
data class Metrics(val hostname: String, val cpu: Double, val memory: Double, val usedGb: Double?, val totalGb: Double?, val gpu: GpuMetrics?)

private fun JSONObject.number(key: String): Double? =
    optDouble(key, Double.NaN).takeIf { it.isFinite() && it >= 0 }
private fun JSONObject.percent(key: String): Double? = number(key)?.takeIf { it <= 100 }

fun parseMetrics(body: String): Metrics {
    val root = JSONObject(body)
    val cpu = root.getJSONObject("cpu")
    val memory = root.getJSONObject("memory")
    val gpu = root.optJSONObject("gpu")?.takeIf { it.optBoolean("available", false) }
    return Metrics(
        hostname = root.optString("hostname", "电脑"),
        cpu = requireNotNull(cpu.percent("usage_percent")) { "CPU 数据无效" },
        memory = requireNotNull(memory.percent("usage_percent")) { "内存数据无效" },
        usedGb = memory.number("used_gb"), totalGb = memory.number("total_gb"),
        gpu = gpu?.let { GpuMetrics(it.optString("name", "GPU"), it.percent("utilization_percent"),
            it.percent("vram_usage_percent"), it.number("vram_used_mb"), it.number("vram_total_mb")) }
    )
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS)
    .readTimeout(2, TimeUnit.SECONDS)
    .callTimeout(4, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

suspend fun fetchMetrics(endpoint: String): Metrics = suspendCancellableCoroutine { continuation ->
    val call = httpClient.newCall(Request.Builder().url(endpoint)
        .header("Accept", "application/json").header("Cache-Control", "no-cache").build())
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val metrics = response.use {
                    require(it.code == 200) { "HTTP ${it.code}" }
                    val body = requireNotNull(it.body).charStream().use { reader ->
                        val buffer = CharArray(131073)
                        var count = 0
                        while (count < buffer.size) {
                            val read = reader.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        require(count < buffer.size) { "响应过大" }
                        String(buffer, 0, count)
                    }
                    parseMetrics(body)
                }
                continuation.resume(metrics)
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            }
        }
    })
}
