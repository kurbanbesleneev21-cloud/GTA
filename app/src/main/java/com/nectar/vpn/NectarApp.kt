package com.nectar.vpn

import android.app.Application
import com.nectar.vpn.data.Store

class NectarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
