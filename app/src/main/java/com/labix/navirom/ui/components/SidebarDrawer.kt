package com.labix.navirom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labix.navirom.data.api.dto.MusicFolderDto
import com.labix.navirom.data.local.LocalMusicFolder

@Composable
fun LibrariesSidebarContent(
    musicFolders: List<MusicFolderDto>,
    selectedMusicFolderIds: Set<String>,
    onSelectMusicFolder: (String?) -> Unit = {},
    onToggleMusicFolder: (String) -> Unit,
    onSelectAllMusicFolders: () -> Unit,
    onDeselectAllMusicFolders: () -> Unit = {},
    localFolders: List<LocalMusicFolder> = emptyList(),
    disabledLocalFolderIds: Set<String> = emptySet(),
    onToggleLocalFolder: (String) -> Unit = {},
    onSelectAllLocalFolders: () -> Unit = {},
    onDeselectAllLocalFolders: () -> Unit = {},
    onGoToSettings: (() -> Unit)? = null,
    onSyncLibrary: () -> Unit,
    onCloseSidebar: () -> Unit,
    str: (String) -> String
) {
    val isAllServerSelected = selectedMusicFolderIds.contains("__ALL__") || (musicFolders.isNotEmpty() && selectedMusicFolderIds.size >= musicFolders.size)
    val hasServerSelection = selectedMusicFolderIds.isNotEmpty()
    val allLocalSelected = disabledLocalFolderIds.isEmpty() && localFolders.isNotEmpty()
    val activeLocalFolderCount = localFolders.count { !disabledLocalFolderIds.contains(it.id) }

    val headerSubtitle = when {
        isAllServerSelected && activeLocalFolderCount > 0 -> "${str("all_libraries_title")} + $activeLocalFolderCount ${str("local_folders_header")}"
        isAllServerSelected -> str("all_libraries_title")
        hasServerSelection && activeLocalFolderCount > 0 -> "${String.format(str("multi_libraries_selected"), selectedMusicFolderIds.size)} + $activeLocalFolderCount ${str("local_folders_header")}"
        hasServerSelection -> String.format(str("multi_libraries_selected"), selectedMusicFolderIds.size)
        activeLocalFolderCount > 0 -> "$activeLocalFolderCount ${str("local_folders_header")} (${str("local_music_folders_title")})"
        else -> str("clear_library_selection")
    }

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp).testTag("libraries_sidebar_drawer"),
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerTonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Drawer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = str("subtab_libraries"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = headerSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onCloseSidebar,
                    modifier = Modifier.size(32.dp).testTag("close_libraries_sidebar_btn")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // LOCAL FOLDERS SECTION (if any present)
                if (localFolders.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GERÄTE-ORDNER (LOKAL)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            TextButton(
                                onClick = {
                                    if (allLocalSelected) onDeselectAllLocalFolders() else onSelectAllLocalFolders()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (allLocalSelected) "Alle aus" else "Alle an",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    items(localFolders, key = { "side_local_${it.id}" }) { folder ->
                        val isChecked = !disabledLocalFolderIds.contains(folder.id)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            onClick = {
                                onToggleLocalFolder(folder.id)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("sidebar_local_folder_${folder.id.hashCode()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isChecked) Icons.Filled.Folder else Icons.Filled.FolderOff,
                                        contentDescription = null,
                                        tint = if (isChecked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isChecked) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${folder.trackCount} ${str("tracks_count")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isChecked) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleLocalFolder(folder.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.tertiary)
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                } else {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = str("no_local_folders_enabled_in_settings"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = str("no_local_folders_sidebar_hint"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                                if (onGoToSettings != null) {
                                    TextButton(
                                        onClick = {
                                            onCloseSidebar()
                                            onGoToSettings()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = str("btn_open_settings_folders"),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // SERVER LIBRARIES SECTION
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SERVER-BIBLIOTHEKEN (NAVIDROME)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!isAllServerSelected && musicFolders.isNotEmpty()) {
                                TextButton(
                                    onClick = onSelectAllMusicFolders,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = str("select_all_libraries"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                            if (hasServerSelection) {
                                TextButton(
                                    onClick = onDeselectAllMusicFolders,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = str("clear_library_selection"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                if (musicFolders.isNotEmpty()) {
                    // "All Libraries" master option
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isAllServerSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            onClick = {
                                if (isAllServerSelected) onDeselectAllMusicFolders() else onSelectAllMusicFolders()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("sidebar_all_libraries_item")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AllInclusive,
                                        contentDescription = null,
                                        tint = if (isAllServerSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = str("all_libraries_title"),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isAllServerSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = str("all_libraries_combined"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isAllServerSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = isAllServerSelected,
                                    onCheckedChange = {
                                        if (isAllServerSelected) onDeselectAllMusicFolders() else onSelectAllMusicFolders()
                                    }
                                )
                            }
                        }
                    }

                    // Specific Music Folders with multi-select support
                    items(musicFolders, key = { it.id }) { folder ->
                        val isChecked = !isAllServerSelected && selectedMusicFolderIds.contains(folder.id)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            onClick = {
                                onToggleMusicFolder(folder.id)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("sidebar_folder_${folder.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Folder ID: ${folder.id}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleMusicFolder(folder.id) }
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = if (localFolders.isNotEmpty()) "Keine Server-Bibliotheken verbunden. Deine lokalen Musik-Ordner sind oben ausgewählt und einsatzbereit." else str("empty_library_desc"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shake tip banner inside sidebar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Shake device for 2s anywhere to play a random song from active libraries!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    onSyncLibrary()
                    onCloseSidebar()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(str("sync_now"))
            }
        }
    }
}
