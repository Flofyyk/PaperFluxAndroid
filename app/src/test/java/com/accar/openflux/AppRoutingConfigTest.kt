package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class AppRoutingConfigTest {
    @Test fun modesKeepIndependentLists() {
        val config = AppRoutingConfig(excluded = setOf("app.direct"), included = setOf("app.vpn"))
        assertEquals(setOf("app.direct"), config.selected)
        assertEquals(setOf("app.vpn"), config.copy(mode = AppRoutingConfig.INCLUDE).selected)
        assertEquals(config.excluded, config.copy(mode = AppRoutingConfig.INCLUDE).excluded)
        assertTrue(runCatching { config.copy(mode = "invalid") }.isFailure)
    }
    @Test fun ownAppAndRemovedPackagesNeverMakeEmptyInclusionLookValid() {
        val config = AppRoutingConfig(AppRoutingConfig.INCLUDE, included = setOf("app.self", "app.removed"))
        assertTrue(config.usableSelection("app.self") { it != "app.removed" }.isEmpty())
        assertEquals(setOf("app.vpn"), config.copy(included = config.included + "app.vpn")
            .usableSelection("app.self") { it != "app.removed" })
    }
}
