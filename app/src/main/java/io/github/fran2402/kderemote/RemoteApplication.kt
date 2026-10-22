package io.github.fran2402.kderemote

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.github.fran2402.kderemote.kdeconnect.KdeConnectManager
import io.github.fran2402.kderemote.kdeconnect.KeyValueStore

class RemoteApplication : Application() {

    lateinit var manager: KdeConnectManager
        private set

    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        manager = KdeConnectManager(
            identityStore = PreferencesStore(getSharedPreferences("identity", MODE_PRIVATE)),
            trustStore = PreferencesStore(getSharedPreferences("trusted_devices", MODE_PRIVATE)),
            settings = PreferencesStore(getSharedPreferences("settings", MODE_PRIVATE)),
            deviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL,
            log = { Log.d("KdeRemote", it) },
        )
        // Some Wi-Fi drivers drop broadcasts (desktop announcements) without this lock.
        multicastLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("kde-remote")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
        manager.start()

        val connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = manager.refresh()
            },
        )
    }
}

private class PreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun keys(): Set<String> = prefs.all.keys.toSet()
}
