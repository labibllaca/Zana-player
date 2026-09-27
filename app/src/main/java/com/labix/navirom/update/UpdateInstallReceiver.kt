package com.labix.navirom.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

class UpdateInstallReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "UpdateInstallReceiver"
        const val ACTION_INSTALL_STATUS = "com.labix.navirom.ACTION_UPDATE_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "com.labix.navirom.EXTRA_SESSION_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "onReceive called with action: $action")

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No message"
        val otherPackageName = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)

        Log.i(TAG, "Install status: $status, message: $message, otherPackage: $otherPackageName")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System package installer requires the user to confirm the update
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmIntent)
                        Log.i(TAG, "Successfully started system update confirmation intent")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start user action confirmation intent", e)
                    }
                } else {
                    Log.w(TAG, "STATUS_PENDING_USER_ACTION but EXTRA_INTENT was null")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "App update successfully installed by system package installer")
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Log.i(TAG, "Update was aborted or cancelled by user")
            }
            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                Log.w(TAG, "Update was blocked by device policy or parental controls")
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                Log.e(TAG, "Update conflict: package signatures or version conflict")
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Log.e(TAG, "Update failed: insufficient disk storage space")
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                Log.e(TAG, "Update failed: APK file was invalid or corrupted")
            }
            else -> {
                Log.d(TAG, "PackageInstaller received status: $status ($message)")
            }
        }
    }
}
