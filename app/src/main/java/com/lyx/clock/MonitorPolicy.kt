package com.lyx.clock

import java.net.URI

const val DEFAULT_ENDPOINT = "http://192.168.50.10:18888/api/v1/metrics"

class MonitorPolicy {
    var online = false
        private set
    val delayMillis: Long get() = if (online) 1_000L else 30_000L
    fun record(success: Boolean) { online = success }
}

fun validEndpoint(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
        (uri.port == -1 || uri.port in 1..65535) && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)
