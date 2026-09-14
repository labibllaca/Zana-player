package com.labix.navirom.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * Handles hardware and Bluetooth media button intents (AVRCP, wired headsets,
 * Bluetooth A2DP/HFP headsets, steering wheel controls, smartwatch remotes).
 *
 * Supports single-press, double-press (next track), and triple-press (previous track)
 * on single-button Bluetooth earbuds (AirPods, Galaxy Buds, Soundcore, etc.),
 * as well as direct KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_PREVIOUS, KEYCODE_MEDIA_FAST_FORWARD,
 * and KEYCODE_MEDIA_REWIND events.
 */
class NaviromMediaButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NaviromMediaButton"
        private const val MULTI_CLICK_TIMEOUT_MS = 380L

        private val mainHandler = Handler(Looper.getMainLooper())
        private var headSetHookClickCount = 0
        private var pendingClickRunnable: Runnable? = null

        fun handleMediaKeyEvent(context: Context, keyEvent: KeyEvent): Boolean {
            val keyCode = keyEvent.keyCode
            val action = keyEvent.action

            // If it's ACTION_UP, consume it for recognized media keys so duplicate callbacks aren't triggered
            if (action != KeyEvent.ACTION_DOWN) {
                return isMediaKeyCode(keyCode)
            }

            val player = AudioPlayerController.getInstance(context.applicationContext)

            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_NAVIGATE_NEXT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    Log.d(TAG, "Media Next triggered (keyCode=$keyCode)")
                    player.next()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    Log.d(TAG, "Media Previous triggered (keyCode=$keyCode)")
                    player.previous()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    Log.d(TAG, "Media Play triggered")
                    player.resume()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    Log.d(TAG, "Media Pause/Stop triggered")
                    player.pause()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    handleMultiClick(player)
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (keyEvent.isLongPress || keyEvent.repeatCount == 1) {
                        Log.d(TAG, "Volume Up long press -> Next track")
                        player.next()
                        return true
                    }
                    return false
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (keyEvent.isLongPress || keyEvent.repeatCount == 1) {
                        Log.d(TAG, "Volume Down long press -> Previous track")
                        player.previous()
                        return true
                    }
                    return false
                }

                else -> return false
            }
        }

        private fun isMediaKeyCode(keyCode: Int): Boolean {
            return when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_NAVIGATE_NEXT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_L1 -> true
                else -> false
            }
        }

        private fun handleMultiClick(player: AudioPlayerController) {
            headSetHookClickCount++
            pendingClickRunnable?.let { mainHandler.removeCallbacks(it) }

            if (headSetHookClickCount >= 3) {
                // Triple click: previous track
                headSetHookClickCount = 0
                pendingClickRunnable = null
                Log.d(TAG, "Triple click detected -> Previous track")
                player.previous()
            } else {
                val runnable = Runnable {
                    val count = headSetHookClickCount
                    headSetHookClickCount = 0
                    pendingClickRunnable = null
                    when (count) {
                        1 -> {
                            Log.d(TAG, "Single click -> Toggle play/pause")
                            player.togglePlayPause()
                        }
                        2 -> {
                            Log.d(TAG, "Double click -> Next track")
                            player.next()
                        }
                    }
                }
                pendingClickRunnable = runnable
                mainHandler.postDelayed(runnable, MULTI_CLICK_TIMEOUT_MS)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (Intent.ACTION_MEDIA_BUTTON == action) {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            } ?: return

            val handled = handleMediaKeyEvent(context, keyEvent)
            if (handled && isOrderedBroadcast) {
                abortBroadcast()
            }
        }
    }
}
