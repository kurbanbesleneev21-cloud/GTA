package com.nectar.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nectar.vpn.R
import com.nectar.vpn.core.XrayConfig
import com.nectar.vpn.core.XrayCore
import com.nectar.vpn.data.AppData
import com.nectar.vpn.data.Server
import com.nectar.vpn.data.Store
import com.nectar.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The VPN service.
 *
 * Besides a normal "connect / disconnect" it can stay in a *standby* mode for auto-VPN:
 * it watches which app is in the foreground and connects only while one of the
 * chosen apps is open. While connected it checks the connection health and restarts
 * the core or switches to the fastest server when the current one stops working.
 */
class NectarVpnService : VpnService() {

    companion object {
        private const val TAG = "NectarVpn"
        const val ACTION_START = "com.nectar.vpn.START"
        const val ACTION_STOP = "com.nectar.vpn.STOP"
        const val ACTION_STANDBY = "com.nectar.vpn.STANDBY"
        const val ACTION_STANDBY_OFF = "com.nectar.vpn.STANDBY_OFF"
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500

        private const val HEALTH_FIRST_DELAY = 15_000L
        private const val HEALTH_INTERVAL = 25_000L
        private const val HEALTH_FAILS_TO_ACT = 2
        private const val AUTO_OFF_GRACE = 5_000L

        fun start(context: Context) = send(context, ACTION_START, foreground = true)
        fun stop(context: Context) = send(context, ACTION_STOP, foreground = false)
        fun standby(context: Context) = send(context, ACTION_STANDBY, foreground = true)
        fun standbyOff(context: Context) = send(context, ACTION_STANDBY_OFF, foreground = false)

        private fun send(context: Context, action: String, foreground: Boolean) {
            val i = Intent(context, NectarVpnService::class.java).setAction(action)
            try {
                if (foreground) ContextCompat.startForegroundService(context, i) else context.startService(i)
            } catch (e: Exception) {
                Log.w(TAG, "cannot start service: ${e.message}")
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var tun: ParcelFileDescriptor? = null

    private var healthJob: Job? = null
    private var watcherJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** VPN was started by the auto-VPN watcher (so the watcher may also stop it). */
    @Volatile private var autoStarted = false
    /** The user stopped VPN while this app was open: don't auto-start until they leave it. */
    @Volatile private var suppressedPackage: String? = null
    @Volatile private var foregroundPackage: String? = null

    private val settings: AppData get() = Store.data.value
    private val standbyEnabled: Boolean get() = settings.autoVpn && settings.autoApps.isNotEmpty()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                suppressedPackage = foregroundPackage?.takeIf { it in settings.autoApps }
                autoStarted = false
                scope.launch { disconnect(); stayOrStop() }
            }
            ACTION_STANDBY -> {
                showStandbyNotification()
                ensureWatcher()
            }
            ACTION_STANDBY_OFF -> {
                watcherJob?.cancel(); watcherJob = null
                if (VpnState.status.value.phase == Phase.STOPPED) stopSelfCompletely()
            }
            else -> { // ACTION_START, or restarted by the system
                startForegroundCompat(getString(R.string.app_name), "Подключение…")
                autoStarted = false
                scope.launch { connect() }
                if (standbyEnabled) ensureWatcher()
            }
        }
        return START_STICKY
    }

    // ================================================================== connect

    private suspend fun connect(target: Server? = null, reason: String? = null) = lock.withLock {
        val server = target ?: Store.selectedServer()
        if (server == null) {
            VpnState.set(VpnStatus(Phase.STOPPED, error = "Добавьте подписку или ключ"))
            stayOrStop()
            return@withLock
        }
        val previous = VpnState.status.value
        VpnState.set(VpnStatus(Phase.CONNECTING, server.id, server.name))
        try {
            XrayCore.init(this)
            val core = XrayCore.controller
            if (core.isRunning) runCatching { core.stopLoop() }

            val s = settings
            val config = XrayConfig.forVpn(server.config, MTU, s.bypassLan)
            val builder = Builder()
                .setSession(server.name)
                .setMtu(MTU)
                .addAddress("10.10.14.1", 30)
                .addDnsServer(s.dns)
                .addRoute("0.0.0.0", 0)
            if (s.ipv6) {
                builder.addAddress("fc00::10:10:14:1", 126)
                builder.addRoute("::", 0)
            }
            applySplitTunneling(builder, s)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            builder.setConfigureIntent(mainIntent())

            // establish the new interface before closing the old one -> no traffic leak on reconnect
            val fd = builder.establish() ?: error("Нет разрешения на VPN")
            val old = tun
            tun = fd
            runCatching { old?.close() }

            core.startLoop(config, fd.fd)
            if (!core.isRunning) error("Ядро не запустилось")

            val since = if (previous.phase == Phase.CONNECTED && previous.since > 0) previous.since else System.currentTimeMillis()
            VpnState.set(VpnStatus(Phase.CONNECTED, server.id, server.name, since, info = reason))
            startForegroundCompat(server.name, reason ?: "Подключено")
            startHealthMonitor()
            registerNetworkWatch()
            refreshTile()
        } catch (e: Throwable) {
            Log.e(TAG, "connect failed", e)
            runCatching { if (XrayCore.controller.isRunning) XrayCore.controller.stopLoop() }
            closeTun()
            VpnState.set(VpnStatus(Phase.STOPPED, error = e.message ?: e.javaClass.simpleName))
            refreshTile()
            stayOrStop()
        }
    }

    private fun applySplitTunneling(builder: Builder, s: AppData) {
        val self = packageName
        val apps = s.splitApps.filter { it != self && isInstalled(it) }
        when {
            s.splitMode == AppData.SPLIT_ONLY && apps.isNotEmpty() ->
                apps.forEach { runCatching { builder.addAllowedApplication(it) } }
            s.splitMode == AppData.SPLIT_EXCEPT -> {
                builder.addDisallowedApplication(self)
                apps.forEach { runCatching { builder.addDisallowedApplication(it) } }
            }
            else -> builder.addDisallowedApplication(self)
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private suspend fun disconnect() = lock.withLock {
        if (VpnState.status.value.phase == Phase.STOPPED && tun == null) return@withLock
        VpnState.set(VpnState.status.value.copy(phase = Phase.STOPPING))
        healthJob?.cancel(); healthJob = null
        unregisterNetworkWatch()
        runCatching { if (XrayCore.controller.isRunning) XrayCore.controller.stopLoop() }
        closeTun()
        VpnState.set(VpnStatus(Phase.STOPPED))
        refreshTile()
    }

    /** After a disconnect: keep running in standby for auto-VPN, otherwise stop the service. */
    private fun stayOrStop() {
        if (standbyEnabled) {
            showStandbyNotification()
            ensureWatcher()
        } else {
            stopSelfCompletely()
        }
    }

    private fun stopSelfCompletely() {
        watcherJob?.cancel(); watcherJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTun() {
        runCatching { tun?.close() }
        tun = null
    }

    // ================================================================== health / auto restart

    private fun startHealthMonitor() {
        healthJob?.cancel()
        healthJob = scope.launch {
            delay(HEALTH_FIRST_DELAY)
            var fails = 0
            var restarts = 0
            while (isActive) {
                val ok = runCatching { XrayCore.controller.measureDelay(XrayCore.TEST_URL) > 0 }.getOrDefault(false)
                if (ok) {
                    fails = 0; restarts = 0
                } else {
                    fails++
                    Log.w(TAG, "health check failed ($fails)")
                }
                val s = settings
                if (fails >= HEALTH_FAILS_TO_ACT && s.autoReconnect) {
                    fails = 0
                    restarts++
                    val current = Store.findServer(VpnState.status.value.serverId)
                    if (restarts >= 2 && s.autoSwitch) {
                        restarts = 0
                        val best = Store.findFastest(this@NectarVpnService, exclude = current?.id)
                        if (best != null) {
                            Store.edit { it.copy(selectedServerId = best.id) }
                            scope.launch { connect(best, "Сервер не отвечал — переключено") }
                            return@launch
                        }
                    }
                    scope.launch { connect(current, "Соединение перезапущено") }
                    return@launch
                }
                delay(HEALTH_INTERVAL)
            }
        }
    }

    private fun registerNetworkWatch() {
        if (networkCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null
            private var pending: Job? = null
            override fun onAvailable(network: Network) {
                val prev = current
                current = network
                if (prev == null || prev == network) return
                if (!settings.reconnectOnNetworkChange) return
                if (VpnState.status.value.phase != Phase.CONNECTED) return
                pending?.cancel()
                pending = scope.launch {
                    delay(1500) // let the new network settle
                    val server = Store.findServer(VpnState.status.value.serverId)
                    connect(server, "Сеть сменилась — переподключено")
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    private fun unregisterNetworkWatch() {
        val cb = networkCallback ?: return
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        networkCallback = null
    }

    // ================================================================== auto VPN by app

    private fun ensureWatcher() {
        if (watcherJob?.isActive == true) return
        watcherJob = scope.launch {
            var leftAt = 0L
            while (isActive) {
                val s = settings
                if (!s.autoVpn || s.autoApps.isEmpty()) {
                    if (VpnState.status.value.phase == Phase.STOPPED) {
                        stopSelfCompletely()
                    }
                    return@launch
                }
                val fg = AppUsage.foregroundPackage(this@NectarVpnService) ?: foregroundPackage
                if (fg != foregroundPackage) {
                    foregroundPackage = fg
                    if (suppressedPackage != null && fg != suppressedPackage && fg != packageName) suppressedPackage = null
                }
                val wanted = fg != null && fg in s.autoApps && fg != suppressedPackage
                val phase = VpnState.status.value.phase
                if (wanted) {
                    leftAt = 0
                    if (phase == Phase.STOPPED && VpnService.prepare(this@NectarVpnService) == null) {
                        autoStarted = true
                        startForegroundCompat(getString(R.string.app_name), "Авто-VPN: подключение…")
                        connect(reason = "Авто-VPN")
                    }
                } else if (autoStarted && phase == Phase.CONNECTED && fg != packageName) {
                    if (leftAt == 0L) leftAt = System.currentTimeMillis()
                    else if (System.currentTimeMillis() - leftAt > AUTO_OFF_GRACE) {
                        autoStarted = false
                        leftAt = 0
                        disconnect()
                        showStandbyNotification()
                    }
                }
                delay(1500)
            }
        }
    }

    // ================================================================== lifecycle

    override fun onRevoke() {
        // another VPN app took over or the user revoked the permission
        autoStarted = false
        scope.launch { disconnect(); stopSelfCompletely() }
    }

    override fun onDestroy() {
        healthJob?.cancel()
        watcherJob?.cancel()
        unregisterNetworkWatch()
        runCatching { if (XrayCore.controller.isRunning) XrayCore.controller.stopLoop() }
        closeTun()
        if (VpnState.status.value.phase != Phase.STOPPED) VpnState.set(VpnStatus(Phase.STOPPED))
        refreshTile()
        scope.cancel()
        super.onDestroy()
    }

    // ================================================================== notification

    private fun mainIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(title: String, text: String, withStop: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(mainIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF000000.toInt())
        if (withStop) {
            val stopIntent = PendingIntent.getService(
                this, 1, Intent(this, NectarVpnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "Отключить", stopIntent)
        }
        return b.build()
    }

    private fun startForegroundCompat(title: String, text: String, withStop: Boolean = true) {
        val n = buildNotification(title, text, withStop)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun showStandbyNotification() {
        val n = settings.autoApps.size
        startForegroundCompat("Авто-VPN включён", "Подключится при открытии выбранных приложений ($n)", withStop = false)
    }

    private fun refreshTile() {
        runCatching {
            TileService.requestListeningState(this, ComponentName(this, QuickTileService::class.java))
        }
    }
}
