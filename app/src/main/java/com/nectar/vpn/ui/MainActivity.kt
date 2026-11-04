package com.nectar.vpn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONNECT = "connect"
    }

    private val vm: MainViewModel by viewModels()

    private var afterVpnPermission: (() -> Unit)? = null

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val then = afterVpnPermission
        afterVpnPermission = null
        if (it.resultCode == RESULT_OK) then?.invoke()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NectarTheme {
                MainScreen(vm = vm, onConnect = ::requestConnect, onPrepareVpn = ::withVpnPermission)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
            vm.refreshDue()
            if (VpnService.prepare(this) == null) vm.ensureStandby()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.data?.toString()?.let { vm.addInput(it) }
        if (intent.getBooleanExtra(EXTRA_CONNECT, false)) requestConnect()
    }

    private fun requestConnect() = withVpnPermission { vm.connect() }

    /** Runs [then] once the VPN permission is granted (asks the user if needed). */
    private fun withVpnPermission(then: () -> Unit) {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            afterVpnPermission = then
            vpnPermission.launch(prepare)
        } else then()
    }
}
