package com.lyx.clock

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class NetworkCancellationTest {
    @Test fun cancellationClosesAnInFlightSocket() = runBlocking {
        val server = ServerSocket(0)
        val received = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n{".toByteArray())
                        flush()
                    }
                    received.countDown()
                    if (reader.read() == -1) disconnected.countDown()
                }
            } catch (_: Exception) { }
        }
        val request = launch(Dispatchers.IO) { fetchMetrics("http://127.0.0.1:${server.localPort}/metrics") }
        try {
            assertTrue(received.await(3, TimeUnit.SECONDS))
            withTimeout(1000) { request.cancelAndJoin() }
            assertTrue("Cancelling must close the socket promptly", disconnected.await(1, TimeUnit.SECONDS))
        } finally {
            request.cancel()
            server.close()
            worker.join(5500)
        }
    }
}
