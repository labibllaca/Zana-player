package com.labix.navirom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labix.navirom.data.api.dto.MusicFolderDto
import com.labix.navirom.data.local.LocalMusicFolder
import com.labix.navirom.ui.NaviromTab

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
    profileName: String = "",
    serverConnected: Boolean = true,
    onNavigateToTab: ((NaviromTab) -> Unit)? = null,
    currentTab: NaviromTab = NaviromTab.LIBRARY,
    str: (String) -> String
) {
    val isAllServerSelected = selectedMusicFolderIds.contains("__ALL__") || (musicFolders.isNotEmpty() && selectedMusicFolderIds.size >= musicFolders.size)
    val hasServerSelection = selectedMusicFolderIds.isNotEmpty()
    val allLocalSelected = disabledLocalFolderIds.isEmpty() && localFolders.isNotEmpty()
    val activeLocalFolderCount = localFolders.count { !disabledLocalFolderIds.contains(it.id) }
    val displayName = if (profileName.isNotBlank()) profileName.trim() else "User"

    ModalDrawerSheet(
        modifier = Modifier.width(330.dp).testTag("libraries_sidebar_drawer"),
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerTonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            // Header inspired by MenuMobile.jpg: Typographic brand mark + Close cross
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ZANA",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 4.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onCloseSidebar,
                    modifier = Modifier.size(36.dp).testTag("close_libraries_sidebar_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Primary Navigation Links (Inspired by MenuMobile.jpg bold vertical menu)
            if (onNavigateToTab != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SidebarNavMenuItem(
                        label = str("tab_library"),
                        isSelected = currentTab == NaviromTab.LIBRARY,
                        onClick = {
                            onNavigateToTab(NaviromTab.LIBRARY)
                            onCloseSidebar()
                        }
                    )
                    SidebarNavMenuItem(
                        label = str("tab_playlists"),
                        isSelected = currentTab == NaviromTab.PLAYLISTS,
                        onClick = {
                            onNavigateToTab(NaviromTab.PLAYLISTS)
                            onCloseSidebar()
                        }
                    )
                    SidebarNavMenuItem(
                        label = str("tab_search"),
                        isSelected = currentTab == NaviromTab.SEARCH,
                        onClick = {
                            onNavigateToTab(NaviromTab.SEARCH)
                            onCloseSidebar()
                        }
                    )
                    SidebarNavMenuItem(
                        label = str("tab_settings"),
                        isSelected = currentTab == NaviromTab.SETTINGS,
                        onClick = {
                            onNavigateToTab(NaviromTab.SETTINGS)
                            onCloseSidebar()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Scrollable Libraries Section
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section Header
                item {
                    Text(
                        text = "COLLECTIONS & STORAGE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // LOCAL FOLDERS SECTION
                if (localFolders.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LOCAL STORAGE",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (allLocalSelected) "All On" else "Custom",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    if (allLocalSelected) onDeselectAllLocalFolders() else onSelectAllLocalFolders()
                                }
                            )
                        }
                    }

                    items(localFolders, key = { "side_local_${it.id}" }) { folder ->
                        val isChecked = !disabledLocalFolderIds.contains(folder.id)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isChecked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
                            ),
                            onClick = { onToggleLocalFolder(folder.id) },
                            modifier = Modifier.fillMaxWidth().testTag("sidebar_local_folder_${folder.id.hashCode()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${folder.trackCount} ${str("tracks_count")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleLocalFolder(folder.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                // SERVER LIBRARIES SECTION
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SERVER LIBRARIES",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasServerSelection) {
                            Text(
                                text = if (isAllServerSelected) "All (${musicFolders.size})" else "${selectedMusicFolderIds.size} selected",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    if (isAllServerSelected) onDeselectAllMusicFolders() else onSelectAllMusicFolders()
                                }
                            )
                        }
                    }
                }

                if (musicFolders.isNotEmpty()) {
                    // All Libraries item
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAllServerSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isAllServerSelected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
                            ),
                            onClick = {
                                if (isAllServerSelected) onDeselectAllMusicFolders() else onSelectAllMusicFolders()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("sidebar_all_libraries_item")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = str("all_libraries_title"),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = str("all_libraries_combined"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isAllServerSelected,
                                    onCheckedChange = {
                                        if (isAllServerSelected) onDeselectAllMusicFolders() else onSelectAllMusicFolders()
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    // Individual Music Folders
                    items(musicFolders, key = { it.id }) { folder ->
                        val isChecked = !isAllServerSelected && selectedMusicFolderIds.contains(folder.id)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isChecked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
                            ),
                            onClick = { onToggleMusicFolder(folder.id) },
                            modifier = Modifier.fillMaxWidth().testTag("sidebar_folder_${folder.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Folder ID: ${folder.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleMusicFolder(folder.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // Bottom User Profile & Sync (Directly reflecting MenuMobile.jpg profile element)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (serverConnected) Color(0xFF2D7A4F) else MaterialTheme.colorScheme.error)
                            )
                            Text(
                                text = if (serverConnected) "Navidrome Online" else "Offline / Local",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Sync button
                IconButton(
                    onClick = {
                        onSyncLibrary()
                        onCloseSidebar()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .testTag("sidebar_sync_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = str("sync_now"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarNavMenuItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 1.sp
        ),
        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

