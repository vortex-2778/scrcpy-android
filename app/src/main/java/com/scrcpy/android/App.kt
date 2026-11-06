package com.scrcpy.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    lateinit var appScope: CoroutineScope
        private set

    override fun onCreate() {
        super.onCreate()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
