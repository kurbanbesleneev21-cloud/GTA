package com.nectar.vpn.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Helpers that build / adapt Xray JSON configs. */
object XrayConfig {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8",
        "169.254.0.0/16", "100.64.0.0/10", "fc00::/7", "fe80::/10", "::1/128",
    )

    private val SERVICE_PROTOCOLS = setOf("freedom", "blackhole", "dns", "loopback")

    /** Wraps a single outbound (from a share link) into a complete config. */
    fun fromOutbound(outbound: JsonObject, bypassLan: Boolean = false): String {
        val root = JsonObject()
        root.add("log", JsonObject().apply { addProperty("loglevel", "warning") })
        root.add("dns", JsonObject().apply {
            add("servers", JsonArray().apply { add("1.1.1.1"); add("8.8.8.8") })
        })
        root.add("inbounds", JsonArray())
        root.add("outbounds", JsonArray().apply {
            add(outbound)
            add(JsonObject().apply { addProperty("tag", "direct"); addProperty("protocol", "freedom") })
            add(JsonObject().apply { addProperty("tag", "block"); addProperty("protocol", "blackhole") })
        })
        root.add("routing", JsonObject().apply {
            addProperty("domainStrategy", "AsIs")
            add("rules", JsonArray().apply {
                if (bypassLan) add(JsonObject().apply {
                    addProperty("type", "field")
                    add("ip", JsonArray().apply { PRIVATE_CIDRS.forEach { add(it) } })
                    addProperty("outboundTag", "direct")
                })
            })
        })
        return gson.toJson(root)
    }

    fun parse(config: String): JsonObject? = runCatching { JsonParser.parseString(config).asJsonObject }.getOrNull()

    /** Main proxy outbound of a config (tag "proxy" or the first non-service one). */
    private fun mainOutbound(cfg: JsonObject): JsonObject? {
        val outs = cfg.getAsJsonArray("outbounds") ?: return null
        val list = outs.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
        return list.firstOrNull { it.get("tag")?.asString == "proxy" }
            ?: list.firstOrNull { (it.get("protocol")?.asString ?: "") !in SERVICE_PROTOCOLS }
    }

    /** Short human-readable description like "VLESS · XHTTP · REALITY". */
    fun describe(config: String): String {
        val cfg = parse(config) ?: return "JSON"
        val balancers = cfg.getAsJsonObject("routing")?.getAsJsonArray("balancers")
        val proxyCount = cfg.getAsJsonArray("outbounds")?.count {
            it.isJsonObject && (it.asJsonObject.get("protocol")?.asString ?: "") !in SERVICE_PROTOCOLS
        } ?: 0
        val ob = mainOutbound(cfg) ?: return "JSON"
        val protocol = ob.get("protocol")?.asString.orEmpty().let { if (it == "hysteria") "HYSTERIA2" else it.uppercase() }
        val ss = ob.getAsJsonObject("streamSettings")
        val network = (ss?.get("network")?.asString ?: "tcp").let { if (it == "raw") "tcp" else it }.uppercase()
        val security = (ss?.get("security")?.asString ?: "none").uppercase()
        val parts = mutableListOf(protocol)
        if (network != "HYSTERIA") parts += network
        if (security != "NONE") parts += security
        if (balancers != null && balancers.size() > 0 && proxyCount > 1) parts += "AUTO ×$proxyCount"
        return parts.joinToString(" · ")
    }

    private fun sniffing() = JsonObject().apply {
        addProperty("enabled", true)
        add("destOverride", JsonArray().apply { add("http"); add("tls"); add("quic") })
    }

    /**
     * Makes the config ready for the VPN: adds a "tun" inbound which reads packets from the
     * VpnService file descriptor. If the config already has a socks inbound we turn it into the
     * tun inbound (keeping its tag) so that routing rules bound to it keep working.
     */
    fun forVpn(config: String, mtu: Int, bypassLan: Boolean): String {
        val cfg = parse(config) ?: error("Некорректная конфигурация сервера")
        val inbounds = cfg.getAsJsonArray("inbounds") ?: JsonArray().also { cfg.add("inbounds", it) }
        val kept = JsonArray()
        var tunTag: String? = null
        var tunSniffing: JsonObject? = null
        for (e in inbounds) {
            if (!e.isJsonObject) continue
            val ib = e.asJsonObject
            when (ib.get("protocol")?.asString) {
                "tun" -> {}
                "socks", "mixed" -> if (tunTag == null) {
                    tunTag = ib.get("tag")?.asString
                    tunSniffing = ib.getAsJsonObject("sniffing")
                } else kept.add(ib)
                else -> kept.add(ib)
            }
        }
        kept.add(JsonObject().apply {
            addProperty("tag", tunTag ?: "tun")
            addProperty("protocol", "tun")
            add("settings", JsonObject().apply {
                addProperty("name", "xray0")
                addProperty("MTU", mtu)
                addProperty("userLevel", 8)
            })
            add("sniffing", tunSniffing ?: sniffing())
        })
        cfg.add("inbounds", kept)
        if (bypassLan) addLanBypass(cfg)
        if (!cfg.has("log")) cfg.add("log", JsonObject())
        cfg.getAsJsonObject("log").apply {
            addProperty("loglevel", "warning")
            remove("access"); remove("error")
        }
        return gson.toJson(cfg)
    }

    /** Puts a "private networks -> direct" rule in front of all routing rules. */
    private fun addLanBypass(cfg: JsonObject) {
        val outs = cfg.getAsJsonArray("outbounds") ?: return
        var directTag = outs.firstOrNull {
            it.isJsonObject && it.asJsonObject.get("protocol")?.asString == "freedom" && it.asJsonObject.has("tag")
        }?.asJsonObject?.get("tag")?.asString
        if (directTag == null) {
            directTag = "direct-lan"
            outs.add(JsonObject().apply { addProperty("tag", directTag); addProperty("protocol", "freedom") })
        }
        val routing = cfg.getAsJsonObject("routing") ?: JsonObject().also { cfg.add("routing", it) }
        val rules = routing.getAsJsonArray("rules") ?: JsonArray()
        val newRules = JsonArray()
        newRules.add(JsonObject().apply {
            addProperty("type", "field")
            add("ip", JsonArray().apply { PRIVATE_CIDRS.forEach { add(it) } })
            addProperty("outboundTag", directTag)
        })
        rules.forEach { newRules.add(it) }
        routing.add("rules", newRules)
    }

    /** Config for an in-process delay test: no inbounds at all. */
    fun forTest(config: String): String {
        val cfg = parse(config) ?: return config
        cfg.add("inbounds", JsonArray())
        cfg.add("log", JsonObject().apply { addProperty("loglevel", "none") })
        return gson.toJson(cfg)
    }

    fun toPretty(obj: JsonObject): String = gson.toJson(obj)
}
