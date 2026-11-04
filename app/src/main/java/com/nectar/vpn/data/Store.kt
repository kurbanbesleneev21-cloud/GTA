package com.nectar.vpn.data

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Very small persistent store: the whole app state is kept in one JSON file. */
object Store {
    private val gson = GsonBuilder().create()
    private lateinit var file: File
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "nectar.json")
        // Merge the saved values over the defaults so that settings added in newer
        // versions get their default value instead of false/null.
        val loaded = runCatching {
            if (!file.exists()) return@runCatching null
            val defaults = gson.toJsonTree(AppData()).asJsonObject
            val saved = JsonParser.parseString(file.readText()).asJsonObject
            for ((k, v) in saved.entrySet()) if (!v.isJsonNull) defaults.add(k, v)
            gson.fromJson(defaults, AppData::class.java)
        }.getOrNull()
        _data.value = sanitize(loaded ?: AppData())
    }

    /** Gson bypasses Kotlin constructors, so null-check everything that came from disk. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitize(d: AppData): AppData = d.copy(
        subscriptions = (d.subscriptions ?: emptyList()).map { s ->
            s.copy(
                id = s.id ?: "", url = s.url ?: "", title = s.title ?: "",
                announce = s.announce ?: "", supportUrl = s.supportUrl ?: "",
                updateHours = if (s.updateHours <= 0) 12 else s.updateHours,
                servers = (s.servers ?: emptyList()).map {
                    it.copy(id = it.id ?: "", name = it.name ?: "", label = it.label ?: "", config = it.config ?: "")
                },
            )
        },
        userAgent = d.userAgent?.takeIf { it.isNotBlank() } ?: AppData.DEFAULT_USER_AGENT,
        dns = d.dns?.takeIf { it.isNotBlank() } ?: "1.1.1.1",
        splitMode = d.splitMode ?: AppData.SPLIT_ALL,
        splitApps = d.splitApps ?: emptyList(),
        autoApps = d.autoApps ?: emptyList(),
    )

    @Synchronized
    fun edit(block: (AppData) -> AppData) {
        _data.update(block)
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(gson.toJson(_data.value))
            tmp.renameTo(file)
        }
    }

    fun allServers(): List<Server> = data.value.subscriptions.flatMap { it.servers }

    fun findServer(id: String?): Server? = id?.let { sid -> allServers().firstOrNull { it.id == sid } }

    fun selectedServer(): Server? =
        findServer(data.value.selectedServerId) ?: allServers().firstOrNull()

    /**
     * Pings every server (except [exclude]) and returns the fastest working one,
     * also saving the measured pings. Returns null when nothing answers.
     */
    suspend fun findFastest(context: Context, exclude: String? = null): Server? = kotlinx.coroutines.coroutineScope {
        val candidates = allServers().filter { it.id != exclude && it.config.isNotBlank() }
        val results = candidates.map { s ->
            async { s to com.nectar.vpn.core.XrayCore.ping(context, s.config) }
        }.awaitAll()
        results.forEach { (s, ms) -> setPing(s.id, ms) }
        results.filter { it.second > 0 }.minByOrNull { it.second }?.first
    }

    fun setPing(serverId: String, ping: Long) = edit { d ->
        d.copy(subscriptions = d.subscriptions.map { s ->
            if (s.servers.none { it.id == serverId }) s
            else s.copy(servers = s.servers.map { if (it.id == serverId) it.copy(ping = ping) else it })
        })
    }
}
