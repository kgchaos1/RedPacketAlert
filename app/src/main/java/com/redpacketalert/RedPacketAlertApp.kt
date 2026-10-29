package com.redpacketalert

import android.app.Application

class RedPacketAlertApp : Application() {

    override fun onCreate() {
        super.onCreate()
        RedAlert.init(this)
    }
}
