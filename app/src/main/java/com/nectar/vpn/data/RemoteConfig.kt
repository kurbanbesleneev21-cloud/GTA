package com.nectar.vpn.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Configuration controlled from the Nectar Control admin panel / admin bot. */
data class Remote(
    val updatedAt: Long = 0,
    val announcementOn: Boolean = false,
    val announcementTitle: String = "",
    val announcementText: String = "",
    val announcementUrl: String = "",
    val announcementButton: String = "",
    val popupId: String = "",
    val popupTitle: String = "",
    val popupText: String = "",
    val popupUrl: String = "",
    val popupButton: String = "",
    val maintenance: Boolean = false,
    val maintenanceText: String = "",
    val minVersion: Int = 0,
    val latestVersion: Int = 0,
    val updateUrl: String = "",
    val updateText: String = "",
    val bot: String = Links.BOT,
    val support: String = "",
    val channel: String = "",
    val site: String = "",
    val iphone: String = "",
    val buttons: List<Pair<String, String>> = emptyList(),
    val hide: List<String> = emptyList(),
    val pin: List<String> = emptyList(),
    val rename: Map<String, String> = emptyMap(),
    val extraKeys: List<String> = emptyList(),
    val defaults: JsonObject = JsonObject(),
    val forceDefaults: Boolean = false,
    val featAutoVpn: Boolean = true,
    val featSplit: Boolean = true,
    val featAddManual: Boolean = true,
    val featUserAgent: Boolean = true,
    val userAgent: String = "",
    val testUrl: String = "",
) {
    fun isHidden(name: String) = hide.any { it.isNotBlank() && name.contains(it, ignoreCase = true) }
    fun isPinned(name: String) = pin.any { it.isNotBlank() && name.contains(it, ignoreCase = true) }
    fun displayName(name: String): String =
        rename.entries.firstOrNull { name.contains(it.key, ignoreCase = true) }?.let { name.replace(it.key, it.value) } ?: name

    /** Applies hide / pin rules to a server list. */
    fun arrange(list: List<Server>): List<Server> =
        list.filter { !isHidden(it.name) }.sortedBy { if (isPinned(it.name)) 0 else 1 }
}

object RemoteConfig {
    const val URL = "https://nectar-vpn-control.vercel.app/api/config"
    const val GROUP_ID = "nectar-remote"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _remote = MutableStateFlow(Remote())
    val remote: StateFlow<Remote> = _remote.asStateFlow()
    val current: Remote get() = _remote.value

    private lateinit var cache: File

    fun init(context: Context) {
        cache = File(context.filesDir, "remote.json")
        runCatching { if (cache.exists()) _remote.value = parse(JsonParser.parseString(cache.readText()).asJsonObject) }
    }

    /** Downloads the config and applies it. Safe to call often; errors keep the cached copy. */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(URL).header("User-Agent", "NectarVPN-Android").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false
                val text = resp.body.string()
                val obj = JsonParser.parseString(text).asJsonObject
                val r = parse(obj)
                _remote.value = r
                cache.writeText(text)
                apply(context, r)
                true
            }
        }.getOrDefault(false)
    }

    /** Pushes admin-controlled data into the local store. */
    private fun apply(context: Context, r: Remote) {
        // extra servers given to everybody
        val servers = if (r.extraKeys.isEmpty()) emptyList()
        else SubscriptionFetcher.parseContent(GROUP_ID, r.extraKeys.joinToString("\n"))
        Store.edit { d ->
            var subs = d.subscriptions.filter { it.id != GROUP_ID }
            if (servers.isNotEmpty()) {
                val old = d.subscriptions.firstOrNull { it.id == GROUP_ID }?.servers.orEmpty()
                val merged = servers.map { s -> old.firstOrNull { it.id == s.id }?.let { s.copy(ping = it.ping) } ?: s }
                subs = listOf(Subscription(id = GROUP_ID, title = "Nectar", updatedAt = System.currentTimeMillis(), servers = merged)) + subs
            }
            var next = d.copy(subscriptions = subs)
            // default settings
            if (r.defaults.size() > 0 && (r.forceDefaults || !d.remoteDefaultsApplied)) {
                next = applyDefaults(next, r.defaults).copy(remoteDefaultsApplied = true, remoteDefaultsAt = r.updatedAt)
            }
            if (r.userAgent.isNotBlank()) next = next.copy(userAgent = r.userAgent)
            if (next.selectedServerId == null) next = next.copy(selectedServerId = servers.firstOrNull()?.id)
            next
        }
        com.nectar.vpn.core.XrayCore.testUrl = r.testUrl.ifBlank { com.nectar.vpn.core.XrayCore.DEFAULT_TEST_URL }
    }

    private val ALLOWED_DEFAULTS = setOf(
        "dns", "ipv6", "bypassLan", "autoUpdate", "pingAfterUpdate", "sortByPing", "monoFlags", "haptics",
        "autoReconnect", "autoSwitch", "reconnectOnNetworkChange", "splitMode",
    )

    private fun applyDefaults(d: AppData, defaults: JsonObject): AppData = runCatching {
        val gson = Gson()
        val tree = gson.toJsonTree(d).asJsonObject
        for ((k, v) in defaults.entrySet()) if (k in ALLOWED_DEFAULTS) tree.add(k, v)
        gson.fromJson(tree, AppData::class.java)
    }.getOrDefault(d)

    // ------------------------------------------------------------------ parsing

    private fun JsonObject.obj(k: String): JsonObject = get(k)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
    private fun JsonObject.str(k: String): String = get(k)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun JsonObject.bool(k: String, def: Boolean = false): Boolean =
        get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } ?: def
    private fun JsonObject.int(k: String): Int = get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: 0
    private fun JsonObject.long(k: String): Long = get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0
    private fun JsonElement?.strings(): List<String> =
        this?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()

    fun parse(o: JsonObject): Remote {
        val ann = o.obj("announcement")
        val pop = o.obj("popup")
        val mnt = o.obj("maintenance")
        val upd = o.obj("update")
        val links = o.obj("links")
        val srv = o.obj("servers")
        val feat = o.obj("features")
        return Remote(
            updatedAt = o.long("updatedAt"),
            announcementOn = ann.bool("enabled"),
            announcementTitle = ann.str("title"),
            announcementText = ann.str("text"),
            announcementUrl = ann.str("url"),
            announcementButton = ann.str("buttonText"),
            popupId = pop.str("id"),
            popupTitle = pop.str("title"),
            popupText = pop.str("text"),
            popupUrl = pop.str("url"),
            popupButton = pop.str("buttonText"),
            maintenance = mnt.bool("enabled"),
            maintenanceText = mnt.str("text"),
            minVersion = upd.int("minVersionCode"),
            latestVersion = upd.int("latestVersionCode"),
            updateUrl = upd.str("url"),
            updateText = upd.str("text"),
            bot = links.str("bot").ifBlank { Links.BOT },
            support = links.str("support"),
            channel = links.str("channel"),
            site = links.str("site"),
            iphone = links.str("iphone"),
            buttons = o.get("buttons")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { e ->
                val b = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val t = b.str("title"); val u = b.str("url")
                if (t.isBlank() || u.isBlank()) null else t to u
            }.orEmpty(),
            hide = srv.get("hide").strings(),
            pin = srv.get("pin").strings(),
            rename = srv.obj("rename").entrySet().mapNotNull { (k, v) ->
                v.takeIf { it.isJsonPrimitive }?.asString?.let { k to it }
            }.toMap(),
            extraKeys = o.get("extraKeys").strings(),
            defaults = o.obj("defaults"),
            forceDefaults = o.bool("forceDefaults"),
            featAutoVpn = feat.bool("autoVpn", true),
            featSplit = feat.bool("splitTunnel", true),
            featAddManual = feat.bool("addManual", true),
            featUserAgent = feat.bool("customUserAgent", true),
            userAgent = o.str("userAgent"),
            testUrl = o.str("testUrl"),
        )
    }
}
