package com.nectar.vpn.data

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLDecoder

/** A parsed share link: display name + Xray outbound (tag = "proxy"). */
data class ParsedLink(val name: String, val outbound: JsonObject)

/**
 * Converts share links (vless://, vmess://, trojan://, ss://, hysteria2:// / hy2://)
 * into Xray outbounds.
 */
object LinkParser {

    fun isLink(line: String): Boolean {
        val l = line.trim().lowercase()
        return SCHEMES.any { l.startsWith("$it://") }
    }

    private val SCHEMES = listOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "hysteria")

    fun parse(raw: String): ParsedLink? = runCatching {
        val link = raw.trim()
        when (link.substringBefore("://").lowercase()) {
            "vless" -> parseVless(link)
            "trojan" -> parseTrojan(link)
            "vmess" -> parseVmess(link)
            "ss" -> parseShadowsocks(link)
            "hysteria2", "hy2", "hysteria" -> parseHysteria2(link)
            else -> null
        }
    }.getOrNull()

    // ---------------------------------------------------------------- URI helpers

    private class Uri(
        val user: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val fragment: String,
    )

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    private fun splitUri(link: String): Uri {
        var rest = link.substringAfter("://")
        var fragment = ""
        rest.indexOf('#').takeIf { it >= 0 }?.let {
            fragment = decode(rest.substring(it + 1)); rest = rest.substring(0, it)
        }
        var queryStr = ""
        rest.indexOf('?').takeIf { it >= 0 }?.let {
            queryStr = rest.substring(it + 1); rest = rest.substring(0, it)
        }
        rest = rest.trimEnd('/')
        val at = rest.lastIndexOf('@')
        val user = if (at >= 0) decode(rest.substring(0, at)) else ""
        val hostPort = if (at >= 0) rest.substring(at + 1) else rest
        val host: String
        val port: Int
        if (hostPort.startsWith("[")) {
            host = hostPort.substring(1, hostPort.indexOf(']'))
            port = hostPort.substringAfter("]:", "443").toIntOrNull() ?: 443
        } else {
            host = hostPort.substringBeforeLast(':')
            port = hostPort.substringAfterLast(':', "443").toIntOrNull() ?: 443
        }
        val query = queryStr.split('&').filter { it.contains('=') }.associate {
            it.substringBefore('=').lowercase() to decode(it.substringAfter('='))
        }
        return Uri(user, host, port, query, fragment)
    }

    fun decodeBase64(s: String): String? {
        val clean = s.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        for (flags in listOf(Base64.DEFAULT, Base64.URL_SAFE or Base64.NO_WRAP)) {
            runCatching {
                val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
                return String(Base64.decode(padded, flags), Charsets.UTF_8)
            }
        }
        return null
    }

    private fun strArray(csv: String?): JsonArray? {
        if (csv.isNullOrBlank()) return null
        return JsonArray().apply { csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) } }
    }

    // ---------------------------------------------------------------- stream settings

    /** Builds streamSettings from the common query parameters used by vless/trojan/vmess links. */
    private fun stream(
        network: String,
        security: String,
        q: Map<String, String>,
        defaultSni: String,
    ): JsonObject {
        val net = when (network.lowercase()) {
            "", "raw" -> "tcp"
            "splithttp" -> "xhttp"
            "http", "h2" -> "xhttp"
            else -> network.lowercase()
        }
        val ss = JsonObject()
        ss.addProperty("network", net)
        val host = q["host"].orEmpty()
        val path = q["path"].orEmpty()
        when (net) {
            "tcp" -> if (q["headertype"] == "http") {
                ss.add("tcpSettings", JsonObject().apply {
                    add("header", JsonObject().apply {
                        addProperty("type", "http")
                        add("request", JsonObject().apply {
                            add("path", JsonArray().apply { add(path.ifEmpty { "/" }) })
                            if (host.isNotEmpty()) add("headers", JsonObject().apply { add("Host", strArray(host)) })
                        })
                    })
                })
            }
            "ws" -> ss.add("wsSettings", JsonObject().apply {
                addProperty("path", path.ifEmpty { "/" })
                if (host.isNotEmpty()) addProperty("host", host)
            })
            "httpupgrade" -> ss.add("httpupgradeSettings", JsonObject().apply {
                addProperty("path", path.ifEmpty { "/" })
                if (host.isNotEmpty()) addProperty("host", host)
            })
            "grpc" -> ss.add("grpcSettings", JsonObject().apply {
                addProperty("serviceName", q["servicename"] ?: path)
                q["authority"]?.takeIf { it.isNotEmpty() }?.let { addProperty("authority", it) }
                if (q["mode"] == "multi") addProperty("multiMode", true)
            })
            "xhttp" -> ss.add("xhttpSettings", JsonObject().apply {
                addProperty("path", path.ifEmpty { "/" })
                if (host.isNotEmpty()) addProperty("host", host)
                addProperty("mode", q["mode"]?.takeIf { it.isNotEmpty() } ?: "auto")
                q["extra"]?.takeIf { it.isNotBlank() }?.let { extra ->
                    runCatching { add("extra", JsonParser.parseString(extra)) }
                }
            })
            "kcp", "mkcp" -> ss.addProperty("network", "kcp")
        }

        val sni = q["sni"]?.takeIf { it.isNotEmpty() } ?: q["peer"]?.takeIf { it.isNotEmpty() }
            ?: host.split(',').firstOrNull()?.takeIf { it.isNotEmpty() } ?: defaultSni
        val fp = q["fp"]?.takeIf { it.isNotEmpty() }
        when (security.lowercase()) {
            "tls" -> {
                ss.addProperty("security", "tls")
                ss.add("tlsSettings", JsonObject().apply {
                    addProperty("serverName", sni)
                    addProperty("fingerprint", fp ?: "chrome")
                    strArray(q["alpn"])?.let { add("alpn", it) }
                    if (q["allowinsecure"] == "1" || q["insecure"] == "1" || q["allowinsecure"] == "true") {
                        addProperty("allowInsecure", true)
                    }
                    q["ech"]?.takeIf { it.isNotEmpty() }?.let { addProperty("echConfigList", it) }
                    q["pcs"]?.takeIf { it.isNotEmpty() }?.let { addProperty("pinnedPeerCertSha256", it) }
                })
            }
            "reality" -> {
                ss.addProperty("security", "reality")
                ss.add("realitySettings", JsonObject().apply {
                    addProperty("serverName", sni)
                    addProperty("fingerprint", fp ?: "chrome")
                    addProperty("password", q["pbk"].orEmpty())
                    addProperty("shortId", q["sid"].orEmpty())
                    addProperty("spiderX", q["spx"].orEmpty())
                    q["pqv"]?.takeIf { it.isNotEmpty() }?.let { addProperty("mldsa65Verify", it) }
                })
            }
            else -> ss.addProperty("security", "none")
        }
        return ss
    }

    // ---------------------------------------------------------------- protocols

    private fun parseVless(link: String): ParsedLink {
        val u = splitUri(link)
        val ob = JsonObject().apply {
            addProperty("tag", "proxy")
            addProperty("protocol", "vless")
            add("settings", JsonObject().apply {
                addProperty("address", u.host)
                addProperty("port", u.port)
                addProperty("id", u.user)
                addProperty("encryption", u.query["encryption"]?.takeIf { it.isNotEmpty() } ?: "none")
                u.query["flow"]?.takeIf { it.isNotEmpty() }?.let { addProperty("flow", it) }
                addProperty("level", 8)
            })
            add("streamSettings", stream(u.query["type"].orEmpty(), u.query["security"].orEmpty(), u.query, u.host))
        }
        return ParsedLink(u.fragment.ifEmpty { u.host }, ob)
    }

    private fun parseTrojan(link: String): ParsedLink {
        val u = splitUri(link)
        val ob = JsonObject().apply {
            addProperty("tag", "proxy")
            addProperty("protocol", "trojan")
            add("settings", JsonObject().apply {
                addProperty("address", u.host)
                addProperty("port", u.port)
                addProperty("password", u.user)
                addProperty("level", 8)
            })
            add("streamSettings", stream(u.query["type"].orEmpty(), u.query["security"] ?: "tls", u.query, u.host))
        }
        return ParsedLink(u.fragment.ifEmpty { u.host }, ob)
    }

    private fun parseVmess(link: String): ParsedLink? {
        val json = decodeBase64(link.substringAfter("://"))?.let { JsonParser.parseString(it).asJsonObject } ?: return null
        fun s(k: String) = json.get(k)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val q = mutableMapOf(
            "host" to s("host"), "path" to s("path"), "sni" to s("sni"), "alpn" to s("alpn"),
            "fp" to s("fp"), "headertype" to s("type"), "mode" to s("type"),
        )
        if (s("net") == "grpc") q["servicename"] = s("path")
        val host = s("add")
        val ob = JsonObject().apply {
            addProperty("tag", "proxy")
            addProperty("protocol", "vmess")
            add("settings", JsonObject().apply {
                addProperty("address", host)
                addProperty("port", s("port").toIntOrNull() ?: 443)
                addProperty("id", s("id"))
                addProperty("security", s("scy").ifEmpty { "auto" })
                addProperty("level", 8)
            })
            add("streamSettings", stream(s("net"), s("tls"), q, host))
        }
        return ParsedLink(s("ps").ifEmpty { host }, ob)
    }

    private fun parseShadowsocks(link: String): ParsedLink? {
        var body = link.substringAfter("://")
        val name = body.substringAfter('#', "").let { decode(it) }
        body = body.substringBefore('#').substringBefore('?').trimEnd('/')
        val method: String
        val password: String
        val hostPort: String
        if (body.contains('@')) {
            val userInfo = body.substringBeforeLast('@')
            hostPort = body.substringAfterLast('@')
            val decoded = if (userInfo.contains(':')) decode(userInfo) else decodeBase64(userInfo) ?: return null
            method = decoded.substringBefore(':'); password = decoded.substringAfter(':')
        } else {
            val decoded = decodeBase64(body) ?: return null
            val creds = decoded.substringBeforeLast('@')
            hostPort = decoded.substringAfterLast('@')
            method = creds.substringBefore(':'); password = creds.substringAfter(':')
        }
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
        val ob = JsonObject().apply {
            addProperty("tag", "proxy")
            addProperty("protocol", "shadowsocks")
            add("settings", JsonObject().apply {
                addProperty("address", host)
                addProperty("port", port)
                addProperty("method", method)
                addProperty("password", password)
                addProperty("level", 8)
            })
        }
        return ParsedLink(name.ifEmpty { host }, ob)
    }

    private fun parseHysteria2(link: String): ParsedLink {
        val u = splitUri(link)
        val q = u.query
        val ob = JsonObject().apply {
            addProperty("tag", "proxy")
            addProperty("protocol", "hysteria")
            add("settings", JsonObject().apply {
                addProperty("address", u.host)
                addProperty("port", u.port)
                addProperty("version", 2)
            })
            add("streamSettings", JsonObject().apply {
                addProperty("network", "hysteria")
                addProperty("security", "tls")
                add("tlsSettings", JsonObject().apply {
                    addProperty("serverName", q["sni"]?.takeIf { it.isNotEmpty() } ?: u.host)
                    add("alpn", strArray(q["alpn"]) ?: JsonArray().apply { add("h3") })
                    if (q["insecure"] == "1" || q["allowinsecure"] == "1") addProperty("allowInsecure", true)
                    q["pinsha256"]?.takeIf { it.isNotEmpty() }?.let { addProperty("pinnedPeerCertSha256", it) }
                })
                add("hysteriaSettings", JsonObject().apply {
                    addProperty("version", 2)
                    addProperty("auth", u.user)
                })
                val finalmask = JsonObject()
                if (q["obfs"] == "salamander" && !q["obfs-password"].isNullOrEmpty()) {
                    finalmask.add("udp", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", "salamander")
                            add("settings", JsonObject().apply { addProperty("password", q["obfs-password"]) })
                        })
                    })
                }
                q["mport"]?.takeIf { it.isNotEmpty() }?.let { ports ->
                    finalmask.add("quicParams", JsonObject().apply {
                        add("udpHop", JsonObject().apply {
                            addProperty("ports", ports)
                            addProperty("interval", "30")
                        })
                    })
                }
                if (finalmask.size() > 0) add("finalmask", finalmask)
            })
        }
        return ParsedLink(u.fragment.ifEmpty { u.host }, ob)
    }
}
