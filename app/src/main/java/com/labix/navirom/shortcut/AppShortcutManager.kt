package com.labix.navirom.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.labix.MainActivity
import com.labix.R
import com.labix.navirom.ui.AppLanguage

object AppShortcutManager {
    const val ACTION_CONTINUE_LAST_PLAYED = "com.labix.ACTION_CONTINUE_LAST_PLAYED"
    const val ACTION_START_RANDOM_SERVER = "com.labix.ACTION_START_RANDOM_SERVER"
    const val ACTION_START_RANDOM_LOCAL = "com.labix.ACTION_START_RANDOM_LOCAL"
    const val ACTION_CONNECT_LAST_SERVER = "com.labix.ACTION_CONNECT_LAST_SERVER"

    const val ID_CONTINUE = "shortcut_continue_last_played"
    const val ID_RANDOM_SERVER = "shortcut_start_random_server"
    const val ID_RANDOM_LOCAL = "shortcut_start_random_local"
    const val ID_CONNECT_SERVER = "shortcut_connect_last_server"

    fun updateDynamicShortcuts(context: Context, language: AppLanguage = AppLanguage.ENGLISH) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

            val isSq = language == AppLanguage.ALBANIAN
            val isDe = language == AppLanguage.GERMAN

            val continueShort = when {
                isSq -> "Vazhdo"
                isDe -> "Fortsetzen"
                else -> "Continue"
            }
            val continueLong = when {
                isSq -> "Vazhdo nga kënga e fundit"
                isDe -> "Ab letztem gespielten Titel fortsetzen"
                else -> "Continue from last played song"
            }

            val randomServerShort = when {
                isSq -> "Miks Serveri"
                isDe -> "Server-Mix"
                else -> "Random Mix"
            }
            val randomServerLong = when {
                isSq -> "Fillo miks rastësor nga serveri"
                isDe -> "Zufälligen Server-Mix starten"
                else -> "Start random mix server"
            }

            val randomLocalShort = when {
                isSq -> "Muzikë Lokale"
                isDe -> "Lokale Musik"
                else -> "Random Local"
            }
            val randomLocalLong = when {
                isSq -> "Fillo muzikë të rastësishme lokale"
                isDe -> "Zufällige lokale Musik starten"
                else -> "Start random local"
            }

            val connectServerShort = when {
                isSq -> "Lidhu me Server"
                isDe -> "Server verbinden"
                else -> "Connect Server"
            }
            val connectServerLong = when {
                isSq -> "Lidhu me serverin e fundit"
                isDe -> "Mit letztem Server verbinden"
                else -> "Connect last server"
            }

            val shortcutContinue = ShortcutInfo.Builder(context, ID_CONTINUE)
                .setShortLabel(continueShort)
                .setLongLabel(continueLong)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_continue))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = ACTION_CONTINUE_LAST_PLAYED
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .setRank(1)
                .build()

            val shortcutRandomServer = ShortcutInfo.Builder(context, ID_RANDOM_SERVER)
                .setShortLabel(randomServerShort)
                .setLongLabel(randomServerLong)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_random_server))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = ACTION_START_RANDOM_SERVER
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .setRank(2)
                .build()

            val shortcutRandomLocal = ShortcutInfo.Builder(context, ID_RANDOM_LOCAL)
                .setShortLabel(randomLocalShort)
                .setLongLabel(randomLocalLong)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_random_local))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = ACTION_START_RANDOM_LOCAL
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .setRank(3)
                .build()

            val shortcutConnectServer = ShortcutInfo.Builder(context, ID_CONNECT_SERVER)
                .setShortLabel(connectServerShort)
                .setLongLabel(connectServerLong)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_connect_server))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = ACTION_CONNECT_LAST_SERVER
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .setRank(4)
                .build()

            shortcutManager.dynamicShortcuts = listOf(
                shortcutContinue,
                shortcutRandomServer,
                shortcutRandomLocal,
                shortcutConnectServer
            )
        } catch (e: Exception) {
            android.util.Log.w("AppShortcutManager", "Failed to update dynamic shortcuts", e)
        }
    }
}
