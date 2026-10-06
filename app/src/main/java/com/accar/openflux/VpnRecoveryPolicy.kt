package com.accar.openflux

/** Automatic retries must not expose ordinary apps to the underlying network. */
object VpnRecoveryPolicy {
    fun retainInterface(desired: Boolean, automatic: Boolean, stopping: Boolean) =
        desired && automatic && !stopping
}
