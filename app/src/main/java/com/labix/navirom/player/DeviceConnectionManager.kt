package com.labix.navirom.player

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import com.labix.navirom.data.model.SleepTimerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Helper to manage device connectivity state (Bluetooth, Wi-Fi, Mobile Data)
 * when sleep timer actions are triggered. Supports multi-tier shutdown:
 * 1. Root / Shell command execution (svc, cmd, settings put)
 * 2. System Service Reflection (IWifiManager, IBluetooth)
 * 3. Bluetooth Audio Profile disconnection (A2DP, Headset)
 * 4. AudioManager device clearance
 * 5. Direct legacy/system APIs
 * 6. Interactive System Panel / Settings fallback for unrooted Android 10+ / 13+
 */
class DeviceConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceConnectionMgr"
    }

    private suspend fun runCommand(cmd: String): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(2500L) {
                // 1. Try su (root / emulator userdebug)
                var succeeded = false
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    p.outputStream.close()
                    val exit = p.waitFor()
                    if (exit == 0) succeeded = true
                } catch (_: Throwable) {}

                // 2. Try sh
                if (!succeeded) {
                    try {
                        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                        p.outputStream.close()
                        val exit = p.waitFor()
                        if (exit == 0) succeeded = true
                    } catch (_: Throwable) {}
                }

                // 3. Try direct exec
                if (!succeeded) {
                    try {
                        val p = Runtime.getRuntime().exec(cmd)
                        p.outputStream.close()
                        val exit = p.waitFor()
                        if (exit == 0) succeeded = true
                    } catch (_: Throwable) {}
                }

                succeeded
            } ?: false
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun disableBluetooth() = withContext(Dispatchers.IO) {
        var disabled = false

        // 1. Try root / shell commands
        val commands = listOf(
            "svc bluetooth disable",
            "cmd bluetooth_manager disable",
            "settings put global bluetooth_on 0"
        )
        for (cmd in commands) {
            if (runCommand(cmd)) {
                disabled = true
                Log.d(TAG, "Bluetooth disabled via shell command: $cmd")
                break
            }
        }

        // 2. Try Settings.Global if WRITE_SECURE_SETTINGS or system privilege is available
        try {
            Settings.Global.putInt(context.contentResolver, "bluetooth_on", 0)
            disabled = true
        } catch (_: Throwable) {}

        // 3. Try BluetoothAdapter direct and reflection
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            @Suppress("DEPRECATION")
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                // Try standard disable()
                try {
                    @Suppress("DEPRECATION")
                    if (adapter.isEnabled) {
                        @Suppress("DEPRECATION")
                        val success = adapter.disable()
                        if (success) disabled = true
                        Log.d(TAG, "Bluetooth adapter.disable() called (result: $success)")
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Bluetooth disable SecurityException: ${e.message}")
                } catch (t: Throwable) {
                    Log.w(TAG, "Bluetooth adapter.disable failed: ${t.message}")
                }

                // Try reflection on IBluetooth / mService
                try {
                    val mServiceField = adapter.javaClass.getDeclaredField("mService")
                    mServiceField.isAccessible = true
                    val mService = mServiceField.get(adapter)
                    if (mService != null) {
                        val methods = mService.javaClass.declaredMethods
                        val disableMethod = methods.find { it.name == "disable" }
                        if (disableMethod != null) {
                            disableMethod.isAccessible = true
                            if (disableMethod.parameterTypes.size == 2) {
                                disableMethod.invoke(mService, context.packageName, true)
                            } else if (disableMethod.parameterTypes.size == 1) {
                                disableMethod.invoke(mService, true)
                            }
                            Log.d(TAG, "IBluetooth.disable() invoked via reflection")
                            disabled = true
                        }
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "IBluetooth reflection failed: ${t.message}")
                }

                // 4. Disconnect active Bluetooth audio profiles (A2DP & Headset)
                // This ensures wireless headphones/speakers immediately disconnect
                try {
                    adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            try {
                                val disconnectMethod = proxy.javaClass.methods.find { it.name == "disconnect" }
                                if (disconnectMethod != null) {
                                    disconnectMethod.isAccessible = true
                                    for (device in proxy.connectedDevices) {
                                        disconnectMethod.invoke(proxy, device)
                                        Log.d(TAG, "Disconnected Bluetooth audio device: ${device.name ?: device.address}")
                                    }
                                }
                            } catch (e: Throwable) {
                                Log.w(TAG, "Error disconnecting audio devices on profile $profile", e)
                            } finally {
                                try {
                                    adapter.closeProfileProxy(profile, proxy)
                                } catch (_: Throwable) {}
                            }
                        }
                        override fun onServiceDisconnected(profile: Int) {}
                    }, BluetoothProfile.A2DP)

                    adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            try {
                                val disconnectMethod = proxy.javaClass.methods.find { it.name == "disconnect" }
                                if (disconnectMethod != null) {
                                    disconnectMethod.isAccessible = true
                                    for (device in proxy.connectedDevices) {
                                        disconnectMethod.invoke(proxy, device)
                                        Log.d(TAG, "Disconnected Bluetooth headset device: ${device.name ?: device.address}")
                                    }
                                }
                            } catch (e: Throwable) {
                                Log.w(TAG, "Error disconnecting headset on profile $profile", e)
                            } finally {
                                try {
                                    adapter.closeProfileProxy(profile, proxy)
                                } catch (_: Throwable) {}
                            }
                        }
                        override fun onServiceDisconnected(profile: Int) {}
                    }, BluetoothProfile.HEADSET)
                } catch (e: Throwable) {
                    Log.w(TAG, "Error accessing Bluetooth profile proxies: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed during Bluetooth disable attempt", e)
        }

        // 5. Disconnect Bluetooth routing via AudioManager
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn = false
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AudioManager Bluetooth reset failed: ${t.message}")
        }

        // 6. If Bluetooth is still enabled on unrooted modern Android (API 33+),
        // launch system Bluetooth settings so the user can toggle it off immediately
        if (!disabled) {
            try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                @Suppress("DEPRECATION")
                val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                if (adapter != null && adapter.isEnabled) {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Sleep timer: Please toggle Bluetooth off", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not open Bluetooth settings", t)
            }
        }
    }

    suspend fun disableWifi() = withContext(Dispatchers.IO) {
        var disabled = false

        // 1. Try root / shell commands
        val commands = listOf(
            "svc wifi disable",
            "cmd -w wifi set-wifi-enabled disabled",
            "cmd wifi set-wifi-enabled disabled",
            "settings put global wifi_on 0"
        )
        for (cmd in commands) {
            if (runCommand(cmd)) {
                disabled = true
                Log.d(TAG, "WiFi disabled via shell command: $cmd")
                break
            }
        }

        // 2. Try Settings.Global if WRITE_SECURE_SETTINGS is available
        try {
            Settings.Global.putInt(context.contentResolver, Settings.Global.WIFI_ON, 0)
            disabled = true
        } catch (_: Throwable) {}

        // 3. Try reflection on IWifiManager
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val mServiceField = wifiManager.javaClass.getDeclaredField("mService")
                mServiceField.isAccessible = true
                val mService = mServiceField.get(wifiManager)
                if (mService != null) {
                    val methods = mService.javaClass.declaredMethods
                    val setWifiEnabledMethod = methods.find { it.name == "setWifiEnabled" }
                    if (setWifiEnabledMethod != null) {
                        setWifiEnabledMethod.isAccessible = true
                        if (setWifiEnabledMethod.parameterTypes.size == 2) {
                            setWifiEnabledMethod.invoke(mService, context.packageName, false)
                        } else if (setWifiEnabledMethod.parameterTypes.size == 1) {
                            setWifiEnabledMethod.invoke(mService, false)
                        }
                        Log.d(TAG, "IWifiManager.setWifiEnabled(false) invoked via reflection")
                        disabled = true
                    }
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "IWifiManager reflection failed: ${t.message}")
        }

        // 4. Try legacy API
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = false
                @Suppress("DEPRECATION")
                val res = wifiManager.setWifiEnabled(false)
                @Suppress("DEPRECATION")
                wifiManager.disconnect()
                if (res) disabled = true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy WifiManager disable failed", t)
        }

        // 5. If Wi-Fi is still connected on modern Android (API 29+) without root:
        // Launch system Internet / Wi-Fi panel so user can toggle it off in 1 tap
        if (!disabled) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val isWifi = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (isWifi) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(panelIntent)
                        } catch (_: Throwable) {
                            val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(panelIntent)
                        }
                    } else {
                        val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(settingsIntent)
                    }
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Sleep timer: Please toggle Wi-Fi off", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not open Wi-Fi settings/panel", t)
            }
        }
    }

    suspend fun disableMobileData() = withContext(Dispatchers.IO) {
        // 1. Try shell commands
        val commands = listOf(
            "svc data disable",
            "cmd telephony data disable",
            "settings put global mobile_data 0"
        )
        for (cmd in commands) {
            if (runCommand(cmd)) {
                Log.d(TAG, "Mobile data disabled via shell: $cmd")
                return@withContext
            }
        }

        // 2. Try reflection on TelephonyManager / ConnectivityManager
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null) {
                try {
                    val setMobileDataEnabledMethod = telephonyManager.javaClass.getDeclaredMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
                    setMobileDataEnabledMethod.isAccessible = true
                    setMobileDataEnabledMethod.invoke(telephonyManager, false)
                    Log.d(TAG, "Mobile data disable invoked via TelephonyManager reflection")
                    return@withContext
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
            Log.w(TAG, "Could not disable mobile data directly: ${e.message}")
        }
    }

    suspend fun executeSleepTimerActions(options: SleepTimerOptions) = withContext(Dispatchers.IO) {
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
