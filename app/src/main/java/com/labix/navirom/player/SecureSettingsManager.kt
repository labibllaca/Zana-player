package com.labix.navirom.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object SecureSettingsManager {

    private const val TAG = "SecureSettingsMgr"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    /**
     * Checks whether WRITE_SECURE_SETTINGS has been granted to the app.
     */
    fun isWriteSecureSettingsGranted(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks whether WRITE_SETTINGS (system settings) has been granted.
     */
    fun isWriteSettingsGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    /**
     * Checks if Root (su) is available on the device.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(1500L) {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                p.outputStream.close()
                val exit = p.waitFor()
                exit == 0
            } ?: false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if Shizuku is installed on the device.
     */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Generates the primary ADB command to grant WRITE_SECURE_SETTINGS.
     */
    fun getPrimaryAdbCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * Returns all useful ADB permission grant commands for background connectivity controls.
     */
    fun getAllAdbCommands(context: Context): List<String> {
        val pkg = context.packageName
        return listOf(
            "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "adb shell pm grant $pkg android.permission.WRITE_SETTINGS",
            "adb shell pm grant $pkg android.permission.CHANGE_NETWORK_STATE"
        )
    }

    /**
     * Attempts to automatically grant permissions using Root / privileged shell.
     */
    suspend fun grantPermissionsViaRoot(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val permissions = listOf(
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.WRITE_SETTINGS",
            "android.permission.CHANGE_NETWORK_STATE"
        )

        var anyExecuted = false
        for (perm in permissions) {
            val cmd = "pm grant $pkg $perm"
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                p.outputStream.close()
                val exit = p.waitFor()
                if (exit == 0) {
                    anyExecuted = true
                    Log.i(TAG, "Successfully granted $perm via root")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Root execution failed for $perm: ${e.message}")
            }
        }

        // Return verified permission status
        isWriteSecureSettingsGranted(context)
    }

    /**
     * Opens system Developer Options settings.
     */
    fun openDeveloperSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * Opens system Wireless Debugging settings if available.
     */
    fun openWirelessDebuggingSettings(context: Context): Boolean {
        return try {
            val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            openDeveloperSettings(context)
        }
    }

    /**
     * Launches the Shizuku app if installed.
     */
    fun openShizukuApp(context: Context): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }
}
