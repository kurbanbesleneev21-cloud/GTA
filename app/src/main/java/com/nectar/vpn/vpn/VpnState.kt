package com.nectar.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Phase { STOPPED, CONNECTING, CONNECTED, STOPPING }

data class VpnStatus(
    val phase: Phase = Phase.STOPPED,
    val serverId: String? = null,
    val serverName: String = "",
    val since: Long = 0,
    val error: String? = null,
)

object VpnState {
    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    internal fun set(s: VpnStatus) { _status.value = s }
}
