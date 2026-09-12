package com.labix.navirom.player

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.util.Log
import com.labix.navirom.data.model.SleepTimerOptions

/**
 * Helper to manage device connectivity state (Bluetooth, Wi-Fi, Mobile Data)
 * when sleep timer actions are triggered.
 */
class DeviceConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceConnectionMgr"
    }

    fun disableBluetooth() {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            @Suppress("DEPRECATION")
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                @Suppress("DEPRECATION")
                val success = adapter.disable()
                Log.d(TAG, "Bluetooth disable requested (result: $success)")
            }

            // Also disconnect Bluetooth audio routing via AudioManager
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn = false
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth permission not granted or restricted: ${e.message}")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to disable Bluetooth", e)
        }
    }

    fun disableWifi() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = false
                @Suppress("DEPRECATION")
                wifiManager.setWifiEnabled(false)
                Log.d(TAG, "WiFi disable requested")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "WiFi change permission not granted: ${e.message}")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to disable WiFi", e)
        }
    }

    fun disableMobileData() {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null) {
                try {
                    val setMobileDataEnabledMethod = telephonyManager.javaClass.getDeclaredMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
                    setMobileDataEnabledMethod.isAccessible = true
                    setMobileDataEnabledMethod.invoke(telephonyManager, false)
                    Log.d(TAG, "Mobile data disable invoked via TelephonyManager reflection")
                    return
                } catch (e: Throwable) {
                    Log.d(TAG, "TelephonyManager reflection not available: ${e.message}")
                }
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                try {
                    val setMobileDataEnabledMethod = connectivityManager.javaClass.getDeclaredMethod("setMobileDataEnabled", Boolean::class.javaPrimitiveType)
                    setMobileDataEnabledMethod.isAccessible = true
                    setMobileDataEnabledMethod.invoke(connectivityManager, false)
                    Log.d(TAG, "Mobile data disable invoked via ConnectivityManager reflection")
                } catch (e: Throwable) {
                    Log.d(TAG, "ConnectivityManager reflection not available: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not disable mobile data directly (system-restricted on modern Android): ${e.message}")
        }
    }

    fun executeSleepTimerActions(options: SleepTimerOptions) {
        if (options.disableBluetooth) {
            disableBluetooth()
        }
        if (options.disableWifi) {
            disableWifi()
        }
        if (options.disableMobileData) {
            disableMobileData()
        }
    }
}
