package com.nectar.vpn.data

import android.content.Context
import com.google.gson.GsonBuilder
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
        val loaded = runCatching {
            if (file.exists()) gson.fromJson(file.readText(), AppData::class.java) else null
        }.getOrNull()
        _data.value = sanitize(loaded ?: AppData())
    }

    /** Gson bypasses Kotlin constructors, so null-check everything that came from disk. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitize(d: AppData): AppData = AppData(
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
        selectedServerId = d.selectedServerId,
        userAgent = d.userAgent?.takeIf { it.isNotBlank() } ?: AppData.DEFAULT_USER_AGENT,
        bypassLan = d.bypassLan,
        ipv6 = d.ipv6,
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

    fun setPing(serverId: String, ping: Long) = edit { d ->
        d.copy(subscriptions = d.subscriptions.map { s ->
            if (s.servers.none { it.id == serverId }) s
            else s.copy(servers = s.servers.map { if (it.id == serverId) it.copy(ping = ping) else it })
        })
    }
}
