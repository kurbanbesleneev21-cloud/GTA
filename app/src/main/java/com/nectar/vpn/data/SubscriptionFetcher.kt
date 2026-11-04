package com.nectar.vpn.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nectar.vpn.core.XrayConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit

object SubscriptionFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @SuppressLint("HardwareIds")
    private fun hwid(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "nectar-android"

    /** Downloads and parses a subscription, returning an updated [Subscription]. */
    fun fetch(context: Context, base: Subscription, userAgentSetting: String): Subscription {
        val userAgent = RemoteConfig.current.userAgent.ifBlank { userAgentSetting }
        val request = Request.Builder()
            .url(base.url.trim())
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("x-hwid", hwid(context))
            .header("x-device-os", "Android")
            .header("x-ver-os", Build.VERSION.RELEASE ?: "")
            .header("x-device-model", Build.MODEL ?: "")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Сервер подписки ответил ${resp.code}")
            val body = resp.body.string()
            val servers = parseContent(base.id, body)
            if (servers.isEmpty()) error("В подписке не найдено серверов")

            val info = parseUserInfo(resp.header("subscription-userinfo"))
            val title = decodeHeader(resp.header("profile-title"))
            val interval = resp.header("profile-update-interval")?.trim()?.toIntOrNull()
            return base.copy(
                title = title?.takeIf { it.isNotBlank() } ?: base.title.ifBlank { hostOf(base.url) },
                updatedAt = System.currentTimeMillis(),
                upload = info["upload"] ?: 0,
                download = info["download"] ?: 0,
                total = info["total"] ?: 0,
                expire = (info["expire"] ?: 0).let { if (it > 100_000_000_000L) it / 1000 else it },
                updateHours = interval?.takeIf { it > 0 } ?: base.updateHours,
                announce = decodeHeader(resp.header("announce")).orEmpty(),
                supportUrl = resp.header("support-url").orEmpty(),
                servers = servers.map { s ->
                    // keep the last measured ping for servers that still exist
                    val old = base.servers.firstOrNull { it.id == s.id }
                    if (old != null) s.copy(ping = old.ping) else s
                },
            )
        }
    }

    fun hostOf(url: String): String =
        runCatching { java.net.URI(url.trim()).host }.getOrNull()?.removePrefix("www.") ?: "Подписка"

    private fun decodeHeader(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return null
        return when {
            v.startsWith("base64:", true) -> LinkParser.decodeBase64(v.substring(7))
            v.contains('%') -> runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
            else -> v
        }?.trim()
    }

    private fun parseUserInfo(header: String?): Map<String, Long> {
        if (header.isNullOrBlank()) return emptyMap()
        return header.split(';').mapNotNull { part ->
            val k = part.substringBefore('=').trim().lowercase()
            val v = part.substringAfter('=', "").trim()
            val n = v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
            if (k.isEmpty() || n == null) null else k to n
        }.toMap()
    }

    /**
     * Parses any supported content: a JSON array of Xray configs, a single Xray config,
     * a list of share links, or a base64-encoded list of share links.
     */
    fun parseContent(groupId: String, raw: String): List<Server> {
        val text = raw.trim().removePrefix("﻿")
        val result = mutableListOf<Pair<String, String>>() // name to config

        if (text.startsWith("[") || text.startsWith("{")) {
            val el = runCatching { JsonParser.parseString(text) }.getOrNull()
            val configs = when {
                el == null -> emptyList()
                el.isJsonArray -> el.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }
                el.isJsonObject -> listOf(el.asJsonObject)
                else -> emptyList()
            }
            configs.filter { it.has("outbounds") }.forEachIndexed { i, cfg ->
                val name = cfg.get("remarks")?.takeIf { it.isJsonPrimitive }?.asString ?: "Сервер ${i + 1}"
                result += name to XrayConfig.toPretty(cfg)
            }
        }

        if (result.isEmpty()) {
            val content = if (text.lines().any { LinkParser.isLink(it) }) text
            else LinkParser.decodeBase64(text) ?: text
            content.lines().map { it.trim() }.filter { LinkParser.isLink(it) }.forEach { line ->
                LinkParser.parse(line)?.let { p -> result += p.name to XrayConfig.fromOutbound(p.outbound) }
            }
        }

        val seen = HashMap<String, Int>()
        return result.map { (name, cfg) ->
            val n = seen.merge(name, 1, Int::plus)!!
            Server(
                id = "$groupId|$name|$n",
                name = name,
                label = XrayConfig.describe(cfg),
                config = cfg,
            )
        }
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    @Suppress("unused")
    private fun JsonObject.str(k: String) = get(k)?.takeIf { it.isJsonPrimitive }?.asString
}
