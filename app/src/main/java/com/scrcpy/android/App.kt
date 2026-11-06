package com.scrcpy.android

import android.app.Application
import com.scrcpy.android.data.AppSettings
import com.scrcpy.android.usb.UsbMonitor
import com.scrcpy.android.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var appScope: CoroutineScope
        private set

    lateinit var settings: AppSettings
        private set

    override fun onCreate() {
        super.onCreate()
        singleton = this
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = AppSettings(this)
        appScope.launch {
            runCatching { UsbMonitor.start(this@App) }
                .onFailure { AppLog.e("App", "usb monitor: ${it.message}") }
        }
        AppLog.i("App", "started")
    }

    companion object {
        lateinit var singleton: App
            private set
    }
}

/** Loads the packaged scrcpy-server jar from APK assets. */
object AdbServerAsset {

    /** The server jar is downloaded at CI build time into app/src/main/assets. */
    fun loadServerJar(): ByteArray {
        val bytes = App.singleton.assets.open("scrcpy-server.jar").use { it.readBytes() }
        check(bytes.size > 100_000) { "scrcpy-server.jar asset looks wrong (${bytes.size} bytes)" }
        return bytes
    }
}
