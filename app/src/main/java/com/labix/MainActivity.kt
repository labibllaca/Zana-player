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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labix.navirom.ui.AppThemeMode
import com.labix.navirom.ui.NaviromApp
import com.labix.navirom.ui.NaviromViewModel
import com.labix.navirom.ui.components.AppSplashScreen
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

        // Configure Coil image caching to prevent memory leaks from album arts
        try {
            val imageLoader = coil.ImageLoader.Builder(applicationContext)
                .memoryCache {
                    coil.memory.MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.15)
                        .build()
                }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(64L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
            coil.Coil.setImageLoader(imageLoader)
        } catch (_: Exception) {}

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

            var showSplash by rememberSaveable { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NaviromApp(
                            viewModel = viewModel,
                            onCloseApp = {
                                NaviromPlaybackService.stopService(this@MainActivity)
                                finishAndRemoveTask()
                                finishAffinity()
                            }
                        )

                        AnimatedVisibility(
                            visible = showSplash,
                            enter = fadeIn(),
                            exit = fadeOut(animationSpec = tween(durationMillis = 400))
                        ) {
                            AppSplashScreen(
                                onFinished = { showSplash = false }
                            )
                        }
                    }
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



