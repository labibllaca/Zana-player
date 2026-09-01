package com.labix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import com.labix.navirom.player.NaviromPlaybackService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labix.navirom.ui.AppThemeMode
import com.labix.navirom.ui.NaviromApp
import com.labix.navirom.ui.NaviromViewModel
import com.labix.ui.theme.MyApplicationTheme

import com.labix.navirom.diagnostics.AppDiagnostics

class MainActivity : ComponentActivity() {
    private val viewModel: NaviromViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDiagnostics.init(applicationContext)
        enableEdgeToEdge()

        if (intent?.action == "ACTION_CLOSE_APP") {
            NaviromPlaybackService.stopService(this)
            finishAndRemoveTask()
            finishAffinity()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemDark
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NaviromApp(
                        viewModel = viewModel,
                        onCloseApp = {
                            NaviromPlaybackService.stopService(this)
                            finishAndRemoveTask()
                            finishAffinity()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == "ACTION_CLOSE_APP") {
            NaviromPlaybackService.stopService(this)
            finishAndRemoveTask()
            finishAffinity()
        }
    }
}



