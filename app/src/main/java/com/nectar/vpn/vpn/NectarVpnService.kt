package com.nectar.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import android.content.ComponentName
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nectar.vpn.R
import com.nectar.vpn.core.XrayConfig
import com.nectar.vpn.core.XrayCore
import com.nectar.vpn.data.Store
import com.nectar.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NectarVpnService : VpnService() {

    companion object {
        private const val TAG = "NectarVpn"
        const val ACTION_START = "com.nectar.vpn.START"
        const val ACTION_STOP = "com.nectar.vpn.STOP"
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500

        fun start(context: Context) {
            val i = Intent(context, NectarVpnService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, NectarVpnService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START or system "always-on" start (null action)
                startForegroundCompat(getString(R.string.app_name), "Подключение…")
                scope.launch { startVpn() }
            }
        }
        return START_STICKY
    }

    @Synchronized
    private fun startVpn() {
        val server = Store.selectedServer()
        if (server == null) {
            fail("Добавьте подписку или ключ")
            return
        }
        VpnState.set(VpnStatus(Phase.CONNECTING, server.id, server.name))
        try {
            XrayCore.init(this)
            val core = XrayCore.controller
            if (core.isRunning) runCatching { core.stopLoop() }
            closeTun()

            val settings = Store.data.value
            val config = XrayConfig.forVpn(server.config, MTU, settings.bypassLan)

            val builder = Builder()
                .setSession(server.name)
                .setMtu(MTU)
                .addAddress("10.10.14.1", 30)
                .addDnsServer(settings.dns)
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
            if (settings.ipv6) {
                builder.addAddress("fc00::10:10:14:1", 126)
                builder.addRoute("::", 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            builder.setConfigureIntent(mainIntent())

            val fd = builder.establish() ?: error("Нет разрешения на VPN")
            tun = fd
            core.startLoop(config, fd.fd)
            if (!core.isRunning) error("Ядро не запустилось")

            VpnState.set(VpnStatus(Phase.CONNECTED, server.id, server.name, System.currentTimeMillis()))
            startForegroundCompat(server.name, "Подключено")
            refreshTile()
        } catch (e: Throwable) {
            Log.e(TAG, "start failed", e)
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fail(message: String) {
        runCatching { if (XrayCore.controller.isRunning) XrayCore.controller.stopLoop() }
        closeTun()
        VpnState.set(VpnStatus(Phase.STOPPED, error = message))
        refreshTile()
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopVpn() {
        val cur = VpnState.status.value
        VpnState.set(cur.copy(phase = Phase.STOPPING))
        scope.launch {
            runCatching { if (XrayCore.controller.isRunning) XrayCore.controller.stopLoop() }
            closeTun()
            VpnState.set(VpnStatus(Phase.STOPPED))
            refreshTile()
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun closeTun() {
        runCatching { tun?.close() }
        tun = null
    }

    override fun onRevoke() {
        // another VPN app took over or the user revoked the permission
        stopVpn()
    }

    override fun onDestroy() {
        runCatching { if (XrayCore.controller.isRunning) XrayCore.controller.stopLoop() }
        closeTun()
        if (VpnState.status.value.phase != Phase.STOPPED) VpnState.set(VpnStatus(Phase.STOPPED))
        refreshTile()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------- notification

    private fun mainIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(title: String, text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, NectarVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(mainIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF000000.toInt())
            .addAction(0, "Отключить", stopIntent)
            .build()
    }

    private fun startForegroundCompat(title: String, text: String) {
        val n = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun refreshTile() {
        runCatching {
            TileService.requestListeningState(this, ComponentName(this, QuickTileService::class.java))
        }
    }
}
