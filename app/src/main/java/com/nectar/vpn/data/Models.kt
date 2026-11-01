package com.nectar.vpn.data

/** A single server (one ready-to-run Xray configuration). */
data class Server(
    val id: String = "",
    val name: String = "",
    /** Short description, e.g. "VLESS · XHTTP · REALITY". */
    val label: String = "",
    /** Full Xray JSON config (without the tun inbound, it is added on start). */
    val config: String = "",
    /** 0 = not tested, -1 = failed, >0 = delay in ms. */
    val ping: Long = 0,
)

/** A subscription (or a manual group of keys). */
data class Subscription(
    val id: String = "",
    /** Empty for a manual group of keys. */
    val url: String = "",
    val title: String = "",
    val updatedAt: Long = 0,
    /** Unix seconds, 0 = unknown. */
    val expire: Long = 0,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val updateHours: Int = 12,
    val announce: String = "",
    val supportUrl: String = "",
    val servers: List<Server> = emptyList(),
) {
    val isManual: Boolean get() = url.isBlank()
}

data class AppData(
    val subscriptions: List<Subscription> = emptyList(),
    val selectedServerId: String? = null,
    val userAgent: String = DEFAULT_USER_AGENT,
    val bypassLan: Boolean = true,
    val ipv6: Boolean = true,
) {
    companion object {
        /** Panels (Remnawave, Marzban…) return full Xray-JSON configs for this client signature. */
        const val DEFAULT_USER_AGENT = "Happ/3.0.0"
    }
}
