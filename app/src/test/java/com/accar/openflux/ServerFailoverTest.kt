package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class ServerFailoverTest {
    @Test fun transientFailureKeepsTheSelectedServer() {
        val pool = ServerFailover(); val nodes = listOf("a", "b")
        assertEquals("a", pool.choose(nodes, 0))
        pool.failed("a", 10)
        assertEquals("a", pool.choose(nodes, 20))
        pool.healthy("a")
        pool.failed("a", 30)
        assertEquals("a", pool.choose(nodes, 40))
    }
    @Test fun totalLossSwitchesAndDoesNotChaseThePrimary() {
        val pool = ServerFailover(); val nodes = listOf("a", "b")
        pool.choose(nodes, 0); pool.failed("a", 0, true)
        assertEquals("b", pool.choose(nodes, 1))
        pool.healthy("b")
        assertEquals("b", pool.choose(nodes, 300_001))
    }
    @Test fun allFailedWaitAndRecoverAfterCooldown() {
        val pool = ServerFailover(); val nodes = listOf("a", "b")
        pool.choose(nodes, 0); pool.failed("a", 0, true)
        pool.choose(nodes, 1); pool.failed("b", 1, true)
        assertNull(pool.choose(nodes, 20_000))
        assertEquals("a", pool.choose(nodes, 30_000))
        pool.reset()
        assertEquals("a", pool.choose(nodes, 0))
    }
    @Test fun repeatedStartupFailuresSelectStandby() {
        val pool = ServerFailover(); val nodes = listOf("a", "b")
        pool.choose(nodes, 0); pool.failed("a", 0); pool.failed("a", 1)
        assertEquals("b", pool.choose(nodes, 2))
    }
}
