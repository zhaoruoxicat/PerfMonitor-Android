package com.lyx.clock

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetricsParserTest {
    @Test fun parsesMetricsAndUnavailableGpu() {
        val data = parseMetrics("""{"hostname":"PC","cpu":{"usage_percent":62.2},"memory":{"usage_percent":55.6,"used_gb":17.74,"total_gb":31.93},"gpu":{"available":true,"name":"RTX 5060","utilization_percent":3,"vram_usage_percent":16.6}}""")
        assertEquals(62.2, data.cpu, 0.001)
        assertEquals(55.6, data.memory, 0.001)
        assertEquals(3.0, data.gpu!!.usage!!, 0.001)
        assertEquals(16.6, data.gpu!!.vramPercent!!, 0.001)
        val noGpu = parseMetrics("""{"cpu":{"usage_percent":0},"memory":{"usage_percent":12},"gpu":{"available":false,"utilization_percent":null}}""")
        assertNull(noGpu.gpu)
    }

    @Test fun rejectsInvalidRequiredMetrics() {
        for (json in listOf("{}", "not json", """{"cpu":{"usage_percent":101},"memory":{"usage_percent":20}}""")) {
            assertTrue(runCatching { parseMetrics(json) }.isFailure)
        }
    }
}
