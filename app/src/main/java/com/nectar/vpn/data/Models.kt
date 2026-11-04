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
    /** Local network (192.168.x.x etc.) goes directly, not through the VPN. */
    val bypassLan: Boolean = true,
    val ipv6: Boolean = true,
    /** DNS server given to the system for the VPN interface. */
    val dns: String = "1.1.1.1",
    /** Refresh subscriptions on app start when the update interval has passed. */
    val autoUpdate: Boolean = true,
    /** Measure ping of all servers after a subscription update. */
    val pingAfterUpdate: Boolean = true,
    val sortByPing: Boolean = false,
    /** Grayscale country flags (fits the monochrome style). */
    val monoFlags: Boolean = false,
    /** Haptic feedback on the connect button. */
    val haptics: Boolean = true,

    // --- split tunneling -------------------------------------------------
    /** "all" = every app through VPN, "only" = only [splitApps], "except" = all but [splitApps]. */
    val splitMode: String = SPLIT_ALL,
    val splitApps: List<String> = emptyList(),

    // --- auto VPN by app -------------------------------------------------
    /** Turn VPN on when one of [autoApps] is opened and off when the user leaves it. */
    val autoVpn: Boolean = false,
    val autoApps: List<String> = emptyList(),

    // --- stability ---------------------------------------------------------
    /** Restart the connection when the server stops answering. */
    val autoReconnect: Boolean = true,
    /** If restarting does not help, switch to the fastest working server. */
    val autoSwitch: Boolean = true,
    /** Restart the core when the network changes (Wi-Fi <-> mobile). */
    val reconnectOnNetworkChange: Boolean = true,
) {
    companion object {
        /** Panels (Remnawave, Marzban…) return full Xray-JSON configs for this client signature. */
        const val DEFAULT_USER_AGENT = "Happ/3.0.0"
        const val SPLIT_ALL = "all"
        const val SPLIT_ONLY = "only"
        const val SPLIT_EXCEPT = "except"
    }
}

object Links {
    /** Telegram bot where users buy / renew the subscription. */
    const val BOT = "https://t.me/NECTARVPN_xbot"
}
