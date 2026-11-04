package com.nectar.vpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nectar.vpn.data.Store

/** Restores auto-VPN standby after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val d = Store.data.value
        if (d.autoVpn && d.autoApps.isNotEmpty()) NectarVpnService.standby(context)
    }
}
