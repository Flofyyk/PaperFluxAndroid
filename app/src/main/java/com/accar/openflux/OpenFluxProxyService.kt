package com.accar.openflux

/** A private foreground endpoint, not registered as an Android VPN provider. */
class OpenFluxProxyService : OpenFluxVpnService() {
    override val proxyMode = true
}
