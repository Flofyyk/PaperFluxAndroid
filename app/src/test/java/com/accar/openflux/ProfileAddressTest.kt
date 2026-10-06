package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class ProfileAddressTest {
    @Test fun originalAndExpandedPool() {
        assertTrue(ProfileAddress.valid("10.10.10.2"))
        assertTrue(ProfileAddress.valid("10.10.10.254"))
        assertTrue(ProfileAddress.valid("10.10.11.2"))
        assertTrue(ProfileAddress.valid("10.10.26.49"))
        assertTrue(ProfileAddress.valid("10.10.26.50"))
        assertTrue(ProfileAddress.valid("10.10.89.14"))
    }
    @Test fun boundariesAndNoncanonicalAddresses() {
        for (ip in listOf("10.10.10.1", "10.10.11.0", "10.10.89.15", "10.10.90.2", "10.10.10.255", "10.10.010.2", "127.0.0.1", "10.10.10.2x")) {
            assertFalse(ip, ProfileAddress.valid(ip))
        }
    }
}
