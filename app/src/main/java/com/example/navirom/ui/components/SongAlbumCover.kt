package com.example.navirom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Reusable cover art component for Songs and Albums.
 * When no image/cover exists (or on load error), displays the text:
 * "Shijoe edhe pa foto"
 */
@Composable
fun SongAlbumCover(
    coverArtUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isAlbum: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    customPlaceholderText: String = "Shijoe edhe pa foto",
    textStyle: TextStyle? = null,
    showIcon: Boolean = true
) {
    var isLoadError by remember(coverArtUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!coverArtUrl.isNullOrBlank() && !isLoadError) {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { isLoadError = true }
            )
        } else {
            SongAlbumNoCoverPlaceholder(
                text = customPlaceholderText,
                isAlbum = isAlbum,
                textStyle = textStyle,
                showIcon = showIcon
            )
        }
    }
}

@Composable
fun SongAlbumNoCoverPlaceholder(
    text: String = "Shijoe edhe pa foto",
    isAlbum: Boolean = false,
    textStyle: TextStyle? = null,
    showIcon: Boolean = true,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val maxDim = if (maxWidth < maxHeight) maxWidth else maxHeight

        when {
            maxDim < 56.dp -> {
                // Ultra compact (e.g. 40-48dp thumbnails)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Shijoe\nedhe pa foto",
                        style = textStyle ?: MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.5.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
            maxDim < 140.dp -> {
                // Medium card (e.g. 90-130dp, album cards, quick mix)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    if (showIcon) {
                        Icon(
                            imageVector = if (isAlbum) Icons.Filled.Album else Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            modifier = Modifier.size((maxDim.value * 0.28f).dp.coerceIn(20.dp, 36.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = text,
                        style = textStyle ?: MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            textAlign = TextAlign.Center
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
            else -> {
                // Large artwork (e.g. Full player, album header)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    if (showIcon) {
                        Icon(
                            imageVector = if (isAlbum) Icons.Filled.Album else Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    Text(
                        text = text,
                        style = textStyle ?: MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
