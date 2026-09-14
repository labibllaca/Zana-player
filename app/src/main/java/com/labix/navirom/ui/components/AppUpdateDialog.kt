package com.labix.navirom.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.labix.BuildConfig
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.update.AppUpdateInfo
import com.labix.navirom.update.UpdateState

fun formatReleaseDate(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        val clean = isoDate.replace("Z", "").replace("T", " ")
        if (clean.length >= 16) {
            val datePart = clean.substring(0, 10)
            val timePart = clean.substring(11, 16)
            "$datePart • $timePart"
        } else {
            clean.take(10)
        }
    } catch (_: Exception) {
        isoDate.take(10)
    }
}

@Composable
fun AppUpdateDialog(
    updateState: UpdateState,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onDownloadAndInstall: (AppUpdateInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    val context = LocalContext.current

    when (updateState) {
        is UpdateState.Available -> {
            val info = updateState.updateInfo
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 660.dp)
                        .padding(vertical = 16.dp)
                        .testTag("app_update_dialog")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = str("updates_new_available_title"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${str("updates_published")}: ${formatReleaseDate(info.assetUpdatedAt.ifBlank { info.publishedAt }).ifBlank { info.tagName }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Version Transition & Info Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Current -> New Version Badges
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "v${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = info.tagName,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    if (info.apkSize > 0) {
                                        val mbSize = "%.1f MB".format(info.apkSize / (1024f * 1024f))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Download,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = mbSize,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                if (info.apkName.isNotBlank() && info.apkName != "zana-update.apk") {
                                    Text(
                                        text = "Package: ${info.apkName}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Release Title Headline (if present and meaningful)
                        if (info.title.isNotBlank() && info.title != info.tagName) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = info.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Changelog Section Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = str("updates_changelog"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = info.tagName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Formatted Changelog & Release Notes
                        RichChangelogView(
                            rawChangelog = info.body,
                            appLanguage = appLanguage
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Actions
                        val sizeText = if (info.apkSize > 0) " (%.1f MB)".format(info.apkSize / (1024f * 1024f)) else ""
                        Button(
                            onClick = { onDownloadAndInstall(info) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("update_download_install_btn")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${str("updates_install_btn")}$sizeText", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(str("updates_view_on_github"), style = MaterialTheme.typography.labelMedium)
                            }

                            TextButton(onClick = onDismiss) {
                                Text(str("updates_dismiss"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        is UpdateState.Downloading -> {
            val progress = updateState.progress
            val downloadedMb = "%.1f".format(updateState.downloadedBytes / (1024f * 1024f))
            val totalMb = if (updateState.totalBytes > 0) "%.1f MB".format(updateState.totalBytes / (1024f * 1024f)) else "..."
            val tag = updateState.updateInfo?.tagName ?: ""
            val title = updateState.updateInfo?.title ?: ""

            Dialog(
                onDismissRequest = { /* Don't dismiss during download */ },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VinylInstallationDeck(
                            progress = progress,
                            isInstalling = false,
                            versionTag = tag
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = str("updates_downloading"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (title.isNotBlank() && title != tag) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$downloadedMb MB / $totalMb (${(progress * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = str("updates_ready_install"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is UpdateState.Installing -> {
            val tag = updateState.updateInfo.tagName
            val title = updateState.updateInfo.title

            Dialog(
                onDismissRequest = { /* Don't dismiss during installation */ },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VinylInstallationDeck(
                            progress = 1.0f,
                            isInstalling = true,
                            versionTag = tag
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = str("updates_installing"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (title.isNotBlank() && title != tag) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = str("updates_preparing_installer"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        is UpdateState.UpToDate -> {
            val latest = updateState.latestInfo
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(str("updates_uptodate_title"), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str("updates_uptodate_desc"))
                        if (latest != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${str("updates_current_version")}: v${com.labix.BuildConfig.VERSION_NAME} (Build ${com.labix.BuildConfig.VERSION_CODE})",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${str("updates_server_build")}: ${latest.title.ifBlank { latest.tagName }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (latest.publishedAt.isNotBlank() || latest.assetUpdatedAt.isNotBlank()) {
                                        Text(
                                            text = "${str("updates_published")}: ${formatReleaseDate(latest.assetUpdatedAt.ifBlank { latest.publishedAt })}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (latest.apkSize > 0) {
                                        val mbSize = "%.1f MB".format(latest.apkSize / (1024f * 1024f))
                                        Text(
                                            text = "APK: ${latest.apkName} • $mbSize",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (latest.htmlUrl.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(latest.htmlUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            },
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(str("updates_view_on_github"), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }

        is UpdateState.ReadyToInstall -> {
            val apkFile = updateState.apkFile
            val info = updateState.updateInfo
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VinylInstallationDeck(
                            progress = 1.0f,
                            isInstalling = false,
                            versionTag = info.tagName
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = str("updates_ready_to_install_title"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${info.title.ifBlank { info.tagName }} (${info.apkName})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val updateManager = com.labix.navirom.update.UpdateManager.getInstance(context)
                                updateManager.installApk(context, apkFile, info)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(str("updates_install_now_btn"), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(str("updates_dismiss"))
                        }
                    }
                }
            }
        }

        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(str("updates_error_title"), fontWeight = FontWeight.Bold) },
                text = { Text(updateState.message) },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }

        else -> {}
    }
}

@Composable
fun AppUpdateBanner(
    updateInfo: AppUpdateInfo,
    appLanguage: AppLanguage,
    onOpenUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("app_update_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "${str("updates_new_available_title")} (${updateInfo.tagName})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = str("updates_update_now"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpenUpdate,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(str("updates_update_now"), style = MaterialTheme.typography.labelMedium)
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

sealed class ChangelogItem {
    data class VersionHeader(val title: String) : ChangelogItem()
    data class SectionHeader(val title: String) : ChangelogItem()
    data class Bullet(val text: String, val tag: String? = null, val author: String? = null) : ChangelogItem()
    data class Paragraph(val text: String) : ChangelogItem()
}

fun parseChangelog(raw: String): List<ChangelogItem> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.lines()
    val items = mutableListOf<ChangelogItem>()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue

        // Skip dividers and raw footer diff links
        if (trimmed == "---" || trimmed == "***" || trimmed == "___" ||
            trimmed.startsWith("Full Changelog:", ignoreCase = true) ||
            trimmed.startsWith("See the full diff:", ignoreCase = true) ||
            trimmed.startsWith("**Full Changelog**", ignoreCase = true)
        ) {
            continue
        }

        // Version headers e.g. ### v1.2.0 or ## 1.2.0
        if (trimmed.startsWith("### v", ignoreCase = true) || 
            trimmed.startsWith("## v", ignoreCase = true) || 
            trimmed.startsWith("# v", ignoreCase = true) ||
            trimmed.startsWith("### Version", ignoreCase = true) || 
            trimmed.startsWith("## Version", ignoreCase = true)
        ) {
            val title = trimmed.replace(Regex("^#+\\s*"), "").trim()
            items.add(ChangelogItem.VersionHeader(title))
            continue
        }

        // Section headers e.g. ## What's Changed, ### 🚀 Features, **New Features**
        if (trimmed.startsWith("#")) {
            val title = trimmed.replace(Regex("^#+\\s*"), "").trim()
            items.add(ChangelogItem.SectionHeader(title))
            continue
        }

        if (trimmed.startsWith("**") && trimmed.endsWith("**") && trimmed.length > 4 && !trimmed.contains("\n")) {
            val title = trimmed.removeSurrounding("**").trim()
            items.add(ChangelogItem.SectionHeader(title))
            continue
        }

        // Bullet points: *, -, +, •, or numbered lists (1., 2., etc.)
        val bulletMatch = Regex("^(?:[*+\\-•]|\\d+\\.)\\s+(.*)$").find(trimmed)
        if (bulletMatch != null) {
            var content = bulletMatch.groupValues[1].trim()

            // Extract author if formatted like "... by @username in https://..." or "... by @username"
            var author: String? = null
            val authorMatch = Regex("\\s+by\\s+(@[a-zA-Z0-9_\\-]+)(?:\\s+in\\s+\\S+)?", RegexOption.IGNORE_CASE).find(content)
            if (authorMatch != null) {
                author = authorMatch.groupValues[1]
                content = content.replace(authorMatch.value, "").trim()
            }

            // Remove trailing PR / issue links like "in #123" or "in https://github.com/..."
            content = content.replace(Regex("\\s+in\\s+(?:#[0-9]+|https?://\\S+)", RegexOption.IGNORE_CASE), "").trim()

            // Remove commit hash badges like [1234567] or (1234567)
            content = content.replace(Regex("[\\[(][0-9a-f]{7,10}[)\\]]"), "").trim()

            // Extract category tags like [Feature], [Fix], [UI], [Audio], [Lyrics], [Bug], [Perf]
            var tag: String? = null
            val tagMatch = Regex("^\\[([a-zA-Z0-9_\\-\\s]{2,16})\\]\\s*").find(content)
            if (tagMatch != null) {
                tag = tagMatch.groupValues[1]
                content = content.replace(tagMatch.value, "").trim()
            } else {
                val prefixMatch = Regex("^(feat|fix|perf|ui|style|refactor|chore|docs):\\s*", RegexOption.IGNORE_CASE).find(content)
                if (prefixMatch != null) {
                    tag = prefixMatch.groupValues[1].uppercase()
                    content = content.replace(prefixMatch.value, "").trim()
                }
            }

            // Clean markdown asterisks inside bullet text for clean display
            val cleanText = content.replace("**", "").replace("`", "").trim()

            if (cleanText.isNotBlank()) {
                items.add(ChangelogItem.Bullet(text = cleanText, tag = tag, author = author))
            }
            continue
        }

        // Paragraphs / general text
        val cleanParagraph = trimmed.replace("**", "").replace("`", "").trim()
        if (cleanParagraph.isNotBlank()) {
            items.add(ChangelogItem.Paragraph(cleanParagraph))
        }
    }
    return items
}

@Composable
fun RichChangelogView(
    rawChangelog: String,
    appLanguage: AppLanguage = AppLanguage.GERMAN,
    modifier: Modifier = Modifier
) {
    val items = remember(rawChangelog) { parseChangelog(rawChangelog) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (items.isEmpty()) {
                val fallbackText = when (appLanguage) {
                    AppLanguage.GERMAN -> "• Neue Funktionen, Verbesserungen und Leistungsoptimierungen für die beste Musikwiedergabe."
                    AppLanguage.ALBANIAN -> "• Veçori të reja, përmirësime dhe optimizime të performancës për dëgjim optimal të muzikës."
                    AppLanguage.ENGLISH -> "• New features, improvements, and performance optimizations for the best music playback experience."
                }
                Text(
                    text = fallbackText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                items.forEach { item ->
                    when (item) {
                        is ChangelogItem.VersionHeader -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Stars,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        is ChangelogItem.SectionHeader -> {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ChangelogItem.Bullet -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (item.tag != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    if (item.tag != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        ) {
                                            Text(
                                                text = item.tag,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 17.sp
                                    )
                                    if (item.author != null) {
                                        val authorPrefix = when (appLanguage) {
                                            AppLanguage.GERMAN -> "von"
                                            AppLanguage.ALBANIAN -> "nga"
                                            AppLanguage.ENGLISH -> "by"
                                        }
                                        Text(
                                            text = "$authorPrefix ${item.author}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                        is ChangelogItem.Paragraph -> {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VinylInstallationDeck(
    progress: Float,
    isInstalling: Boolean,
    versionTag: String,
    modifier: Modifier = Modifier
) {
    // 1. Rotation for the vinyl disc
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_transition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isInstalling) 1600 else 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rotation"
    )

    // 2. Sound ripple wave animations
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_scale"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )

    // 3. Tonearm needle tracking angle
    // At progress 0%: ~16 deg (outer lead-in groove)
    // At progress 100%: ~38 deg (inner lead-out groove)
    val targetNeedleAngle = when {
        isInstalling -> 36f
        progress in 0f..1f -> 16f + (progress * 22f)
        else -> 24f
    }
    val needleAngle by animateFloatAsState(
        targetValue = targetNeedleAngle,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "needle_angle"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .size(190.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Acoustic wave pulse rings
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(rippleScale)
                .background(primaryColor.copy(alpha = rippleAlpha), CircleShape)
        )

        // Turntable Platter Base / Dark Chassis Rim
        Box(
            modifier = Modifier
                .size(148.dp)
                .background(Color(0xFF0F0F12), CircleShape)
                .border(BorderStroke(2.dp, Color(0xFF2A2A32)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Spinning Vinyl Disc with Micro-grooves and light reflection
            Canvas(
                modifier = Modifier
                    .size(144.dp)
                    .graphicsLayer { rotationZ = rotationAngle }
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Vinyl body
                drawCircle(
                    color = Color(0xFF141418),
                    radius = radius,
                    center = center
                )

                // Concentric Micro-Grooves
                val grooveSteps = listOf(0.92f, 0.86f, 0.80f, 0.74f, 0.68f, 0.62f, 0.56f, 0.50f, 0.44f, 0.38f)
                grooveSteps.forEachIndexed { index, frac ->
                    val grooveAlpha = if (index % 2 == 0) 0.12f else 0.06f
                    drawCircle(
                        color = Color.White.copy(alpha = grooveAlpha),
                        radius = radius * frac,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Dynamic Dual-Wedge Gloss / Specular Sheen
                val sweepBrush = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.10f to Color.White.copy(alpha = 0.15f),
                    0.20f to Color.Transparent,
                    0.50f to Color.Transparent,
                    0.60f to Color.White.copy(alpha = 0.15f),
                    0.70f to Color.Transparent,
                    1.0f to Color.Transparent,
                    center = center
                )
                drawCircle(
                    brush = sweepBrush,
                    radius = radius * 0.94f,
                    center = center
                )

                // Run-out groove near label
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = radius * 0.36f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Center Vinyl Label
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                primaryColor,
                                primaryContainer
                            )
                        )
                    )
                    .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isInstalling) Icons.Filled.SystemUpdate else Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    if (versionTag.isNotBlank()) {
                        Text(
                            text = versionTag.take(8),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1
                        )
                    }
                }

                // Spindle Hole
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF0F0F12), CircleShape)
                        .border(BorderStroke(1.2.dp, Color.White.copy(alpha = 0.9f)), CircleShape)
                )
            }
        }

        // Turntable Tonearm with Stylus
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 4.dp)
        ) {
            // Tonearm pivot base
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFB0B4BC),
                                Color(0xFF4A4E58)
                            )
                        ),
                        CircleShape
                    )
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF22242A), CircleShape)
                )
            }

            // Rotating arm tube & cartridge
            Canvas(
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        this.rotationZ = needleAngle
                        this.transformOrigin = TransformOrigin(0.14f, 0.14f)
                    }
            ) {
                val startX = size.width * 0.14f
                val startY = size.height * 0.14f
                val midX = size.width * 0.55f
                val midY = size.height * 0.65f
                val endX = size.width * 0.72f
                val endY = size.height * 0.85f

                // Metallic Arm Tube
                drawLine(
                    color = Color(0xFFD8DCE4),
                    start = Offset(startX, startY),
                    end = Offset(midX, midY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFC0C4CC),
                    start = Offset(midX, midY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Cartridge head
                drawCircle(
                    color = Color(0xFF1E2026),
                    radius = 4.5.dp.toPx(),
                    center = Offset(endX, endY)
                )

                // Glowing stylus needle indicator
                drawCircle(
                    color = if (isInstalling) Color(0xFF4CAF50) else primaryColor,
                    radius = 2.dp.toPx(),
                    center = Offset(endX, endY)
                )
            }
        }
    }
}

