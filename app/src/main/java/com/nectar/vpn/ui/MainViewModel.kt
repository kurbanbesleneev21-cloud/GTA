package com.nectar.vpn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nectar.vpn.core.XrayCore
import com.nectar.vpn.data.AppData
import com.nectar.vpn.data.Server
import com.nectar.vpn.data.Store
import com.nectar.vpn.data.Subscription
import com.nectar.vpn.data.SubscriptionFetcher
import com.nectar.vpn.vpn.NectarVpnService
import com.nectar.vpn.vpn.Phase
import com.nectar.vpn.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val data = Store.data
    val vpn = VpnState.status

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = _refreshing.asStateFlow()

    private val _pinging = MutableStateFlow<Set<String>>(emptySet())
    val pinging = _pinging.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _collapsed = MutableStateFlow<Set<String>>(emptySet())
    val collapsed = _collapsed.asStateFlow()

    private val ctx get() = getApplication<Application>()

    fun consumeMessage() { _message.value = null }
    private fun say(msg: String) { _message.value = msg }

    // ------------------------------------------------------------ connection

    fun connect() = NectarVpnService.start(ctx)
    fun disconnect() = NectarVpnService.stop(ctx)

    fun select(server: Server) {
        val wasActive = Store.data.value.selectedServerId != server.id
        Store.edit { it.copy(selectedServerId = server.id) }
        val phase = VpnState.status.value.phase
        if (wasActive && (phase == Phase.CONNECTED || phase == Phase.CONNECTING)) {
            NectarVpnService.start(ctx) // reconnect to the new server
        }
    }

    // ------------------------------------------------------------ subscriptions

    /** Accepts a subscription URL, deep link, share links or Xray JSON. */
    fun addInput(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val url = extractSubscriptionUrl(text)
        if (url != null) {
            if (Store.data.value.subscriptions.any { it.url.trim() == url }) {
                Store.data.value.subscriptions.first { it.url.trim() == url }.let { refresh(it) }
                return
            }
            val sub = Subscription(
                id = SubscriptionFetcher.newId(),
                url = url,
                title = SubscriptionFetcher.hostOf(url),
            )
            Store.edit { it.copy(subscriptions = it.subscriptions + sub) }
            refresh(sub, selectFirst = true)
            return
        }
        viewModelScope.launch {
            val manualId = Store.data.value.subscriptions.firstOrNull { it.isManual }?.id ?: SubscriptionFetcher.newId()
            val parsed = withContext(Dispatchers.Default) { SubscriptionFetcher.parseContent(manualId, text) }
            if (parsed.isEmpty()) {
                say("Не удалось распознать ссылку или ключ")
                return@launch
            }
            Store.edit { d ->
                val existing = d.subscriptions.firstOrNull { it.id == manualId }
                val subs = if (existing == null) {
                    d.subscriptions + Subscription(id = manualId, title = "Мои ключи", updatedAt = System.currentTimeMillis(), servers = parsed)
                } else {
                    val merged = existing.servers.filter { old -> parsed.none { it.id == old.id } } + parsed
                    d.subscriptions.map { if (it.id == manualId) it.copy(servers = merged, updatedAt = System.currentTimeMillis()) else it }
                }
                d.copy(subscriptions = subs, selectedServerId = d.selectedServerId ?: parsed.first().id)
            }
            say("Добавлено серверов: ${parsed.size}")
        }
    }

    private fun extractSubscriptionUrl(text: String): String? {
        if (text.contains('\n')) return null
        val t = text.trim()
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) return t
        // nectarvpn://add/<url>, nectar://add/<url>, happ://add/<url>, …?url=<url>
        val lower = t.lowercase()
        if (lower.startsWith("nectarvpn://") || lower.startsWith("nectar://") || lower.startsWith("happ://")) {
            val q = t.substringAfter("url=", "")
            val candidate = if (q.isNotEmpty()) java.net.URLDecoder.decode(q, "UTF-8") else t.substringAfter("add/", "")
            if (candidate.startsWith("http", true)) return candidate
        }
        return null
    }

    fun refresh(sub: Subscription, selectFirst: Boolean = false) {
        if (sub.isManual || sub.id in _refreshing.value) return
        _refreshing.update { it + sub.id }
        viewModelScope.launch {
            try {
                val ua = Store.data.value.userAgent
                val updated = withContext(Dispatchers.IO) { SubscriptionFetcher.fetch(ctx, sub, ua) }
                Store.edit { d ->
                    val subs = d.subscriptions.map { if (it.id == sub.id) updated else it }
                    val allIds = subs.flatMap { s -> s.servers.map { it.id } }
                    val sel = d.selectedServerId?.takeIf { it in allIds }
                        ?: if (selectFirst || d.selectedServerId == null || d.selectedServerId !in allIds) updated.servers.firstOrNull()?.id else d.selectedServerId
                    d.copy(subscriptions = subs, selectedServerId = sel)
                }
                if (Store.data.value.pingAfterUpdate) pingSubscription(updated)
            } catch (e: Throwable) {
                say("Ошибка обновления: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _refreshing.update { it - sub.id }
            }
        }
    }

    fun refreshAll() = Store.data.value.subscriptions.forEach { refresh(it) }

    /** Called on app start: refreshes subscriptions whose update interval has passed. */
    fun refreshDue() {
        if (!Store.data.value.autoUpdate) return
        val now = System.currentTimeMillis()
        Store.data.value.subscriptions
            .filter { !it.isManual && now - it.updatedAt > it.updateHours * 3_600_000L }
            .forEach { refresh(it) }
    }

    fun delete(sub: Subscription) {
        Store.edit { d ->
            val subs = d.subscriptions.filter { it.id != sub.id }
            val ids = subs.flatMap { s -> s.servers.map { it.id } }
            d.copy(subscriptions = subs, selectedServerId = d.selectedServerId?.takeIf { it in ids } ?: ids.firstOrNull())
        }
    }

    fun deleteServer(server: Server) {
        Store.edit { d ->
            val subs = d.subscriptions.map { s -> s.copy(servers = s.servers.filter { it.id != server.id }) }
                .filter { !(it.isManual && it.servers.isEmpty()) }
            val ids = subs.flatMap { s -> s.servers.map { it.id } }
            d.copy(subscriptions = subs, selectedServerId = d.selectedServerId?.takeIf { it in ids } ?: ids.firstOrNull())
        }
    }

    fun toggleCollapsed(sub: Subscription) = _collapsed.update { if (sub.id in it) it - sub.id else it + sub.id }

    fun collapseAll() {
        val all = Store.data.value.subscriptions.map { it.id }.toSet()
        _collapsed.update { if (it.containsAll(all)) emptySet() else all }
    }

    // ------------------------------------------------------------ ping

    fun pingSubscription(sub: Subscription) = sub.servers.forEach { ping(it) }

    fun pingAll() = Store.data.value.subscriptions.forEach { pingSubscription(it) }

    fun ping(server: Server) {
        if (server.id in _pinging.value) return
        _pinging.update { it + server.id }
        viewModelScope.launch {
            try {
                val ms = XrayCore.ping(ctx, server.config)
                Store.setPing(server.id, ms)
            } finally {
                _pinging.update { it - server.id }
            }
        }
    }

    // ------------------------------------------------------------ settings

    fun updateSettings(block: (AppData) -> AppData) = Store.edit(block)
}
