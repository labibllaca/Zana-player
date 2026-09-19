package com.labix.navirom.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.labix.R
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.util.rememberNaviromHaptics
import com.labix.ui.theme.AccentEmerald
import kotlinx.coroutines.launch

private enum class InitialSetupStage {
    PERMISSIONS,
    ONBOARDING
}

@Composable
fun InitialSetupScreen(
    appLanguage: AppLanguage,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberNaviromHaptics()
    var currentStage by rememberSaveable { mutableStateOf(InitialSetupStage.PERMISSIONS) }

    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

    // Check existing permissions on launch
    val checkAudioPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    val checkNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    var isAudioGranted by remember { mutableStateOf(checkAudioPermission()) }
    var isNotificationGranted by remember { mutableStateOf(checkNotificationPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }
        val notifResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        isAudioGranted = isAudioGranted || audioResult
        isNotificationGranted = isNotificationGranted || notifResult

        // Transition to onboarding tour with light haptic feedback
        haptics.tick()
        currentStage = InitialSetupStage.ONBOARDING
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("initial_setup_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentStage,
            transitionSpec = {
                (fadeIn(animationSpec = tween(350)) + slideInHorizontally(animationSpec = tween(350)) { it / 4 })
                    .togetherWith(fadeOut(animationSpec = tween(250)) + slideOutHorizontally(animationSpec = tween(250)) { -it / 4 })
            },
            label = "setup_stage_transition"
        ) { stage ->
            when (stage) {
                InitialSetupStage.PERMISSIONS -> {
                    PermissionsStageContent(
                        isAudioGranted = isAudioGranted,
                        isNotificationGranted = isNotificationGranted,
                        appLanguage = appLanguage,
                        onRequestPermissions = {
                            val perms = mutableListOf<String>()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (!isAudioGranted) perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                                if (!isNotificationGranted) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                if (!isAudioGranted) perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }

                            if (perms.isNotEmpty()) {
                                permissionLauncher.launch(perms.toTypedArray())
                            } else {
                                haptics.tick()
                                currentStage = InitialSetupStage.ONBOARDING
                            }
                        },
                        onSkip = {
                            haptics.tick()
                            currentStage = InitialSetupStage.ONBOARDING
                        }
                    )
                }

                InitialSetupStage.ONBOARDING -> {
                    OnboardingTourContent(
                        appLanguage = appLanguage,
                        onFinished = {
                            haptics.tick()
                            onFinished()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Phase 1: Essential Permissions Request Screen
 */
@Composable
private fun PermissionsStageContent(
    isAudioGranted: Boolean,
    isNotificationGranted: Boolean,
    appLanguage: AppLanguage,
    onRequestPermissions: () -> Unit,
    onSkip: () -> Unit
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    val scrollState = rememberScrollState()
    val allGranted = isAudioGranted && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || isNotificationGranted)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            // App Badge / Brand Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = str("perm_welcome_title"),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = str("perm_welcome_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Permission Card 1: Music & Audio Access
            PermissionItemCard(
                icon = Icons.Filled.FolderSpecial,
                title = str("perm_audio_title"),
                description = str("perm_audio_desc"),
                isGranted = isAudioGranted,
                statusText = if (isAudioGranted) str("perm_audio_status_granted") else str("perm_audio_status_required"),
                accentColor = MaterialTheme.colorScheme.primary
            )

            // Permission Card 2: Notifications (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(16.dp))

                PermissionItemCard(
                    icon = Icons.Filled.NotificationsActive,
                    title = str("perm_notif_title"),
                    description = str("perm_notif_desc"),
                    isGranted = isNotificationGranted,
                    statusText = if (isNotificationGranted) str("perm_notif_status_granted") else str("perm_notif_status_recommended"),
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }

            if (allGranted) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = AccentEmerald.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = str("perm_all_granted_banner"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Action Buttons at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("permission_grant_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (allGranted) str("perm_btn_continue") else str("perm_btn_grant"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (!allGranted) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("permission_skip_button")
                ) {
                    Text(
                        text = str("perm_btn_skip"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    statusText: String,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isGranted) AccentEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) AccentEmerald.copy(alpha = 0.15f) else accentColor.copy(alpha = 0.15f)
                    )
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.Check else icon,
                    contentDescription = null,
                    tint = if (isGranted) AccentEmerald else accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = if (isGranted) AccentEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isGranted) AccentEmerald.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isGranted) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Phase 2: Interactive Onboarding Tour (Horizontal Pager)
 */
private data class OnboardingSlideData(
    val titleKey: String,
    val subtitleKey: String,
    val descKey: String,
    val featureKeys: List<String>,
    val primaryIcon: ImageVector,
    val secondaryIcon: ImageVector,
    val accentColor: Color
)

@Composable
private fun OnboardingTourContent(
    appLanguage: AppLanguage,
    onFinished: () -> Unit
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberNaviromHaptics()

    val slides = remember {
        listOf(
            OnboardingSlideData(
                titleKey = "onboard_slide1_title",
                subtitleKey = "onboard_slide1_subtitle",
                descKey = "onboard_slide1_desc",
                featureKeys = listOf("onboard_slide1_feat1", "onboard_slide1_feat2", "onboard_slide1_feat3"),
                primaryIcon = Icons.Filled.CloudSync,
                secondaryIcon = Icons.Filled.Folder,
                accentColor = Color(0xFF3B82F6)
            ),
            OnboardingSlideData(
                titleKey = "onboard_slide2_title",
                subtitleKey = "onboard_slide2_subtitle",
                descKey = "onboard_slide2_desc",
                featureKeys = listOf("onboard_slide2_feat1", "onboard_slide2_feat2", "onboard_slide2_feat3"),
                primaryIcon = Icons.Filled.Album,
                secondaryIcon = Icons.Filled.Tune,
                accentColor = Color(0xFFD97706)
            ),
            OnboardingSlideData(
                titleKey = "onboard_slide3_title",
                subtitleKey = "onboard_slide3_subtitle",
                descKey = "onboard_slide3_desc",
                featureKeys = listOf("onboard_slide3_feat1", "onboard_slide3_feat2", "onboard_slide3_feat3"),
                primaryIcon = Icons.Filled.Lyrics,
                secondaryIcon = Icons.Filled.QueueMusic,
                accentColor = Color(0xFF8B5CF6)
            ),
            OnboardingSlideData(
                titleKey = "onboard_slide4_title",
                subtitleKey = "onboard_slide4_subtitle",
                descKey = "onboard_slide4_desc",
                featureKeys = listOf("onboard_slide4_feat1", "onboard_slide4_feat2", "onboard_slide4_feat3"),
                primaryIcon = Icons.Filled.Shuffle,
                secondaryIcon = Icons.Filled.Devices,
                accentColor = Color(0xFF10B981)
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val isLastPage = pagerState.currentPage == slides.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar: Step indicator & Skip affordance
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${slides.size}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            TextButton(
                onClick = onFinished,
                modifier = Modifier.testTag("onboarding_skip_tour_button")
            ) {
                Text(
                    text = str("onboard_skip"),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            val slide = slides[pageIndex]
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Bespoke Visual Artwork Card
                OnboardingHeroVisual(
                    primaryIcon = slide.primaryIcon,
                    secondaryIcon = slide.secondaryIcon,
                    accentColor = slide.accentColor
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Subtitle pill
                Surface(
                    color = slide.accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = str(slide.subtitleKey).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = slide.accentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Main Title
                Text(
                    text = str(slide.titleKey),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = str(slide.descKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 3 Highlights checklist
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    slide.featureKeys.forEach { featKey ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(slide.accentColor.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = slide.accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Text(
                                text = str(featKey),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls: Page Dots & Navigation Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Animated Dots Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(slides.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                    )
                }
            }

            // Next / Get Started Button
            Button(
                onClick = {
                    haptics.tick()
                    if (isLastPage) {
                        onFinished()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("onboarding_action_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isLastPage) str("onboard_get_started") else str("onboard_next"),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isLastPage) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Editorial Hero Graphic for each slide with decorative layers and depth
 */
@Composable
private fun OnboardingHeroVisual(
    primaryIcon: ImageVector,
    secondaryIcon: ImageVector,
    accentColor: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(170.dp)
            .padding(8.dp)
    ) {
        // Decorative background ring
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.25f),
                            accentColor.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Center card with subtle shadow and border
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = primaryIcon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        // Secondary floating badge
        Surface(
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 8.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.background)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = secondaryIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
