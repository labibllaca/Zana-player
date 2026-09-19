package com.labix.navirom.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A playback button that supports both normal tap (skip prev/next)
 * and continuous press-and-hold (rewind/fast-forward seeking).
 */
@Composable
fun HoldablePlaybackButton(
    onClick: () -> Unit,
    onHoldTick: () -> Unit,
    modifier: Modifier = Modifier,
    onHoldStart: () -> Unit = {},
    onHoldEnd: () -> Unit = {},
    enabled: Boolean = true,
    content: @Composable (isHolding: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var holdTriggered = false
                val holdJob = coroutineScope.launch {
                    delay(350)
                    holdTriggered = true
                    isHolding = true
                    onHoldStart()
                    while (isActive) {
                        onHoldTick()
                        delay(150)
                    }
                }
                val upOrCancel = waitForUpOrCancellation()
                holdJob.cancel()
                if (holdTriggered) {
                    onHoldEnd()
                    isHolding = false
                } else if (upOrCancel != null) {
                    onClick()
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        content(isHolding)
    }
}
