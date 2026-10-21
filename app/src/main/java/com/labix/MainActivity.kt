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
import com.labix.navirom.ui.screens.InitialSetupScreen
import com.labix.ui.theme.MyApplicationTheme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.labix.navirom.diagnostics.AppDiagnostics
import java.lang.ref.WeakReference

import com.labix.navirom.shortcut.AppShortcutManager

class MainActivity : ComponentActivity() {
    companion object {
        private var instanceRef: WeakReference<MainActivity>? = null

        fun getInstance(): MainActivity? = instanceRef?.get()

        fun Context.findActivity(): Activity? {
            var ctx: Context? = this
            while (ctx is ContextWrapper) {
                if (ctx is Activity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }

        fun closeApplication(context: Context? = null) {
            try {
                if (context != null) {
                    NaviromPlaybackService.stopService(context)
                }
            } catch (_: Exception) {}

            try {
                val act = instanceRef?.get() ?: context?.findActivity() ?: (context as? Activity)
                act?.finishAndRemoveTask()
                act?.finishAffinity()
            } catch (_: Exception) {}
        }
    }

    private val viewModel: NaviromViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission response handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceRef = WeakReference(this)
        AppDiagnostics.init(applicationContext)

        // Request notification permission on Android 13+ to ensure playback controls appear in shade
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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

        // Handle quick-action launcher shortcuts
        handleShortcutIntent(intent)
        AppShortcutManager.updateDynamicShortcuts(this)

        setContent {
            val themeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()
            val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
            val hasCompletedInitialSetup by viewModel.hasCompletedInitialSetup.collectAsStateWithLifecycle()

            androidx.compose.runtime.LaunchedEffect(appLanguage) {
                AppShortcutManager.updateDynamicShortcuts(this@MainActivity, appLanguage)
            }

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
                        if (!hasCompletedInitialSetup && !showSplash) {
                            InitialSetupScreen(
                                appLanguage = appLanguage,
                                onFinished = {
                                    viewModel.completeInitialSetup()
                                }
                            )
                        } else {
                            NaviromApp(
                                viewModel = viewModel,
                                onCloseApp = {
                                    NaviromPlaybackService.stopService(this@MainActivity)
                                    finishAndRemoveTask()
                                    finishAffinity()
                                }
                            )
                        }

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
            return
        }
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            AppShortcutManager.ACTION_CONTINUE_LAST_PLAYED -> {
                viewModel.continueLastPlayed()
            }
            AppShortcutManager.ACTION_START_RANDOM_SERVER -> {
                viewModel.startRandomMixServer()
            }
            AppShortcutManager.ACTION_START_RANDOM_LOCAL -> {
                viewModel.startRandomLocal()
            }
            AppShortcutManager.ACTION_CONNECT_LAST_SERVER -> {
                viewModel.connectLastServer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instanceRef?.get() == this) {
            instanceRef = null
        }
    }
}



