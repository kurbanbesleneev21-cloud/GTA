package com.nectar.vpn.core

import android.content.Context
import android.util.Log
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

/** Thin wrapper around the Xray-core Android library (libv2ray.aar). */
object XrayCore {
    private const val TAG = "NectarCore"
    const val TEST_URL = "https://www.gstatic.com/generate_204"

    private val initialized = AtomicBoolean(false)
    private val pingLimiter = Semaphore(6)

    fun init(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            try {
                Seq.setContext(context.applicationContext)
                val dir = context.getDir("assets", Context.MODE_PRIVATE).absolutePath
                Libv2ray.initCoreEnv(dir, "")
            } catch (e: Throwable) {
                initialized.set(false)
                Log.e(TAG, "initCoreEnv failed", e)
                throw e
            }
        }
    }

    val controller: CoreController by lazy {
        Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(code: Long, message: String?): Long {
                Log.i(TAG, "core: $message")
                return 0
            }
        })
    }

    fun version(): String = runCatching { Libv2ray.checkVersionX() }.getOrDefault("?")

    /** Real delay through the server, in ms; -1 on failure. */
    suspend fun ping(context: Context, config: String): Long = withContext(Dispatchers.IO) {
        init(context)
        pingLimiter.withPermit {
            try {
                Libv2ray.measureOutboundDelay(XrayConfig.forTest(config), TEST_URL).let { if (it <= 0) -1L else it }
            } catch (e: Throwable) {
                Log.w(TAG, "ping failed: ${e.message}")
                -1L
            }
        }
    }
}
