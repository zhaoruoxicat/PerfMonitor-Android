package com.lyx.clock

import org.junit.Assert.*
import org.junit.Test

class MonitorPolicyTest {
    @Test fun failedRequestClearsPanelAndWaitsThirtySeconds() {
        val policy = MonitorPolicy()
        policy.record(true)
        assertTrue(policy.online)
        assertEquals(1000L, policy.delayMillis)
        policy.record(false)
        assertFalse(policy.online)
        assertEquals(30000L, policy.delayMillis)
        policy.record(true)
        assertTrue(policy.online)
        assertEquals(1000L, policy.delayMillis)
    }

    @Test fun addressMustBeAnHttpEndpoint() {
        assertTrue(validEndpoint(DEFAULT_ENDPOINT))
        assertTrue(validEndpoint("https://desktop.local:18888/api/v1/metrics"))
        assertFalse(validEndpoint("192.168.50.10:18888"))
        assertFalse(validEndpoint("file:///etc/passwd"))
        assertFalse(validEndpoint("http://"))
        assertFalse(validEndpoint("http://host:99999/api"))
    }
}
