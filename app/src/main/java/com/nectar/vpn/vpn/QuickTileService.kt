package com.nectar.vpn.vpn

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nectar.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Quick settings tile: tap to connect / disconnect. */
class QuickTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        job = CoroutineScope(Dispatchers.Main).launch {
            VpnState.status.collectLatest { update(it.phase) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
    }

    private fun update(phase: Phase) {
        val tile = qsTile ?: return
        tile.state = when (phase) {
            Phase.CONNECTED, Phase.CONNECTING -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    override fun onClick() {
        when (VpnState.status.value.phase) {
            Phase.CONNECTED, Phase.CONNECTING -> NectarVpnService.stop(this)
            else -> {
                if (VpnService.prepare(this) == null) {
                    NectarVpnService.start(this)
                } else {
                    val i = Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.EXTRA_CONNECT, true)
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        val pi = android.app.PendingIntent.getActivity(
                            this, 0, i,
                            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                        startActivityAndCollapse(pi)
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(i)
                    }
                }
            }
        }
    }
}
