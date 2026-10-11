package com.lyx.clock

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class MonitorFlowTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun awaitCondition(timeout: Long = 10000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Condition not met within $timeout ms" }
            SystemClock.sleep(100)
        }
    }

    @Test fun onlineRefreshOfflineBackoffRecoveryAndSavedSettings() {
        Configurator.getInstance().waitForIdleTimeout = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
        val original = prefs.getString("endpoint", null)
        val server = ServerSocket(0)
        val healthy = AtomicBoolean(true)
        val requests = CopyOnWriteArrayList<Long>()
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { /* Read HTTP headers. */ }
                        requests.add(SystemClock.elapsedRealtime())
                        val good = healthy.get()
                        val body = if (good) """{"hostname":"TEST-PC","cpu":{"usage_percent":62.2},"memory":{"usage_percent":55.6,"used_gb":17.74,"total_gb":31.93},"gpu":{"available":false}}""" else "offline"
                        val bytes = body.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 ${if (good) "200 OK" else "503 Unavailable"}\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: Exception) { if (server.isClosed) break }
            }
        }
        val endpoint = "http://127.0.0.1:${server.localPort}/metrics"
        prefs.edit().putString("endpoint", endpoint).commit()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            // MIUI can block background launches by instrumentation; shell launch is explicit.
            device.executeShellCommand("am start -W -n com.lyx.clock/.MainActivity")
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitCondition { device.hasObject(By.text("TEST-PC")) }
            assertTrue(device.hasObject(By.text("62.2%")))
            assertTrue(device.hasObject(By.text("显卡不可用")))
            // Ignore initial orientation recreation and measure a settled pair of requests.
            val initialCount = requests.size
            awaitCondition(6000) { requests.size >= initialCount + 3 }
            val interval = requests.last() - requests[requests.size - 2]
            assertTrue("Online interval was $interval ms", interval in 750..2200)
            healthy.set(false)
            awaitCondition(6000) { !device.hasObject(By.text("TEST-PC")) }
            val lastFailure = requests.last()
            val count = requests.size
            device.takeScreenshot(File(context.filesDir, "clock-offline.png"))
            healthy.set(true)
            SystemClock.sleep(25000)
            assertEquals("Offline mode must not poll every second", count, requests.size)
            awaitCondition(10000) { device.hasObject(By.text("TEST-PC")) }
            assertTrue("Recovery probe must wait 30 seconds", requests[count] - lastFailure >= 29500)

            scenario.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(2500)
            val backgroundCount = requests.size
            SystemClock.sleep(2500)
            assertEquals("Background activity must stop polling", backgroundCount, requests.size)
            device.executeShellCommand("am start -W -n com.lyx.clock/.MainActivity")
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitCondition(6000) { device.hasObject(By.text("TEST-PC")) }

            device.click(device.displayWidth / 2, device.displayHeight / 2)
            awaitCondition { device.hasObject(By.text("设置")) }
            device.findObject(By.text("设置")).click()
            awaitCondition { device.hasObject(By.clazz("android.widget.EditText")) }
            device.findObject(By.clazz("android.widget.EditText")).text = "http://127.0.0.1:1/metrics"
            device.findObject(By.text("保存")).click()
            awaitCondition(6000) { !device.hasObject(By.text("TEST-PC")) }
            assertEquals("http://127.0.0.1:1/metrics", prefs.getString("endpoint", null))
            scenario.recreate()
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            awaitCondition { device.hasObject(By.text("设置")) }
            device.findObject(By.text("设置")).click()
            awaitCondition { device.hasObject(By.clazz("android.widget.EditText")) }
            assertEquals("http://127.0.0.1:1/metrics", device.findObject(By.clazz("android.widget.EditText")).text)
        } finally {
            scenario?.close()
            server.close()
            worker.join(3000)
            if (original == null) prefs.edit().remove("endpoint").commit()
            else prefs.edit().putString("endpoint", original).commit()
        }
    }
}
